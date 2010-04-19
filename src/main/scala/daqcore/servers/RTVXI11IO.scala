// Copyright (C) 2010 Oliver Schulz <oliver.schulz@tu-dortmund.de>

// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.


package daqcore.servers

import scala.actors._

import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeoutException

import org.acplt.oncrpc.OncRpcProtocols

import daqcore.oncrpc.vxi11core
import daqcore.actors._
import daqcore.profiles._
import daqcore.util._


class RTVXI11Connector extends Server with VXI11Connector {
  connector =>
  
  class RTVXI11Client(val address: InetAddress) extends Server {
    client =>

    class RTVXI11Link(val device: String, id: Int, maxRecvSize: Int) extends Server with VXI11Link {
      lnk =>
      
      def lid = new vxi11core.Device_Link(id)
      
      def serve = {
        case MsgIO.Read(timeout) => client.forward(Read(lnk, timeout))
        case MsgIO.Write(data) => client.forward(Write(lnk, defaultTimeout, data))
        case Closeable.Close => exit('Closed)
      }
    }

    val defaultTimeout: Int = 10000
    val defaultRequestSize: Int = 0x400000 // 4 Mb
    val defaultMaxRecvSize:Int = 4096

    var clnt: vxi11core.Client = null  
    var rtlinks: Map[String, RTVXI11Link] = null

    protected case class Read(lnk: RTVXI11Link, timeout: Long)
    protected case class Write(lnk: RTVXI11Link, timeout: Long, data: Seq[Byte])
    protected case class Close(lnk: RTVXI11Link)

    override def init() = {
      super.init()
      rtlinks = Map.empty[String, RTVXI11Link]
      info("Opening VXI11 client connection to " + address)
      try {
        clnt = new vxi11core.Client(address, OncRpcProtocols.ONCRPC_TCP)
      }
      catch { case e =>
        error("Could not open VXI11 client connection to " + address)
        exit('OpenFailed)
      }
    }

    def serve = {
      case Read(lnk, timeout) => reply(lnk, timeout)
      case Write(lnk, timeout, data) => write(lnk, timeout, data)
      case Exit(lnk: RTVXI11Link, 'Closed) => closeLink(lnk)
      case connector.Connect(device, timeout) => reply(openLink(device, timeout))
      case Closeable.Close => exit('Closed)

      case Exit(lnk: RTVXI11Link, msg) => msg match {
        case 'Closed => rtlinks -= lnk.device
        case msg => {
          error("Restarting VXI11 link to %s, %s".format(address, lnk.device))
          link(lnk)
          lnk.start()
        }
      }
    }

    override def deinit() = {
      rtlinks foreach { e => closeLink(e._2) }
      rtlinks = null
      clnt.close(); clnt = null;
      info("VXI11 client connection to " + address + "closed")
      super.deinit()
    }

    
    protected def openLink(device: String, timeout: Long): RTVXI11Link = {
      debug("Creating new VXI11 link to %s, device %s".format(address, device))
      require(rtlinks.get(device) == None)
      require(timeout <= Int.MaxValue)
      
      val lparms = new vxi11core.Create_LinkParms
      lparms.lockDevice = false
      lparms.lock_timeout = (if (timeout < 0) defaultTimeout else timeout).toInt
      lparms.device = device

      trace("create_link(..., device = %s)".format(lparms.device))
      val lresp = clnt.create_link_1(lparms)

      trace("create_link error: " + lresp.error.value)

      // Notes from Steve D. Sharple's C/C++ VXI11 Library:
      // We need to check that maxRecvSize is a sane value (ie >0). Believe it
      // or not, on some versions of Agilent Infiniium scope firmware the scope
      // returned "0", which breaks Rule B.6.3 of the VXI-11 protocol. Nevertheless
      // we need to catch this, otherwise the program just hangs.
      val maxRecvSize = if (lresp.maxRecvSize > 0) lresp.maxRecvSize else defaultMaxRecvSize
      
      val lnk = new RTVXI11Link(device, lresp.lid.value, maxRecvSize)
      link(lnk)
      lnk.start()
      lnk
    }

    protected def closeLink(lnk: RTVXI11Link) : Unit = {
      trace("destroy_link(%s)".format(lnk.lid.value))
      val unlinkResp = clnt.destroy_link_1(lnk.lid)
      trace("destroy_link error value: " + unlinkResp.error.value)
    }

    protected def read(lnk: RTVXI11Link, timeout: Long,
      acc: IndexedSeq[IndexedSeq[Byte]] = IndexedSeq.empty[IndexedSeq[Byte]]) :
      ByteCharSeq =
    {
      require(timeout <= Int.MaxValue)
      val rparms = new vxi11core.Device_ReadParms
      rparms.lid = lnk.lid
      rparms.io_timeout = (if (timeout >= 0) timeout else defaultTimeout).toInt
      rparms.lock_timeout = (if (timeout >= 0) timeout else defaultTimeout).toInt
      rparms.requestSize = defaultRequestSize
      rparms.flags = new vxi11core.Device_Flags(0)
      rparms.termChar = 0

      trace("device_read(%s, %s, ...)".format(rparms.lid, rparms.io_timeout))
      val rresp = clnt.device_read_1(rparms)
      // If read rpc call fails, there is nothing to read - retry or fail?
      // Catch/handle OncRpcException and/or IOException?

      trace("device_read error value: " + rresp.error.value)
      
      rresp.error.value match {
        case 4 => throw new TimeoutException("VXI11 read: I/O timeout")
        case 11 => throw new IOException("VXI11 read: Device locked by another link")
        case 17 => throw new IOException("VXI11 read: I/O error")
        case 23 => throw new IOException("VXI11 read: Abort")
        case _ => throw new IOException("VXI11 read: Unknown error")
      }
      
      val rcv_reason_end:Int = 0x04; // End indicator read
      val rcv_reason_chr:Int = 0x02; // Termchr set in flags and matching character transferred
      val rcv_reason_reqcnt:Int = 0x01; // requestSize bytes transferred.

      trace("device_read response reason: " + rresp.reason)
      
      if ((rresp.reason & rcv_reason_reqcnt) != 0)
        throw new IOException("VXI11 read: Request size to small")
      
      val boxedData: IndexedSeq[IndexedSeq[Byte]] = IndexedSeq(rresp.data)
      // if end or chr bit set, read is complete, if not, more chunks to read
      if ((rresp.reason & (rcv_reason_end | rcv_reason_chr)) != 0) {
        trace("Finished reading")
        ByteCharSeq((acc ++ boxedData) flatten)
      } else {
        trace("Partial read")
        read(lnk, timeout, (acc ++ boxedData))
      }
    }
    
    protected def write(lnk: RTVXI11Link, timeout: Long, data: Seq[Byte], lastChunk: Boolean = true) :
      Unit =
    {
      require(timeout <= Int.MaxValue)
      val dataArray = data.toArray
      
      val wparms = new vxi11core.Device_WriteParms
      wparms.lid = lnk.lid
      wparms.io_timeout = (if (timeout >= 0) timeout else defaultTimeout).toInt
      wparms.lock_timeout = (if (timeout >= 0) timeout else defaultTimeout).toInt
      wparms.flags = new vxi11core.Device_Flags(if (lastChunk) 8 else 0);
      wparms.data = dataArray

      //!!! If data size > maxRecvSize, write seveal chunks!

      trace("device_write(%s, %s, ...)".format(wparms.lid, wparms.io_timeout))
      val wresp = clnt.device_write_1(wparms)
      // If write rpc call fails (!= RPC_SUCCESS) - retry or fail?
      // Catch/handle OncRpcException and/or IOException?
      //
      // Notes from Steve D. Sharple's C/C++ VXI11 Library:
      // The instrument did not acknowledge the write, just completely
      // dropped it. There was no vxi11 comms error as such, the 
      // instrument is just being rude. Usually occurs when the instrument
      // is busy.

      val bytesWritten = wresp.size
      trace("device_write error value: " + wresp.error.value)
      trace("bytes written: " + bytesWritten)
      if (bytesWritten < data.length)
        write(lnk, timeout, dataArray.drop(bytesWritten))
    }
  }


  var clients: Map[InetAddress, RTVXI11Client] = null


  override def init() = {
    super.init()
    clients = Map.empty[InetAddress, RTVXI11Client]
  }

  protected case class Connect(device:String, timeout: Long = -1)

  def serve = {
    case VXI11Connector.Connect(to, device, timeout) =>
      getClient(to).forward(Connect(device, timeout))

    case Closeable.Close => exit('Closed)

    case Exit(client: RTVXI11Client, msg) => msg match {
      case 'Closed => clients -= client.address
      case 'OpenFailed => clients -= client.address
      case msg => {
        error("Restarting VXI11 client connection to " + client.address)
        link(client)
        client.start()
      }
    }
  }


  override def deinit() = {
    clients = null
    super.deinit()
  }


  protected def getClient(address: InetAddress) = clients.get(address) match {
    case Some(client) => {
      trace("Re-using existing VXI11 client " + address)
      client
    }
    case None => {
      val rtClient = new RTVXI11Client(address)
      clients += address -> rtClient
      link(rtClient)
      rtClient.start
    }
  }
}