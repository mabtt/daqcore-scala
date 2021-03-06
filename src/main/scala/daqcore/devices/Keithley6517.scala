// Copyright (C) 2011-2013 Oliver Schulz <oliver.schulz@tu-dortmund.de>

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


package daqcore.devices

import scala.language.postfixOps

import akka.actor._
import scala.concurrent.{Future, Promise}

import daqcore.util._
import daqcore.io._
import daqcore.actors._, daqcore.actors.TypedActorTraits._

import daqcore.io.prot.scpi._, daqcore.io.prot.scpi.mnemonics._

import collection.immutable.Queue


object Keithley6517 {
  val currentReadingFmt = """(.*)(.)ADC,.*,.*rdng#.*""".r
  val voltageReadingFmt = """(.*)(.)VDC,.*,.*rdng#.*""".r

  val ZCHeck = Mnemonic("ZCHeck")
  val SYSTem = Mnemonic("SYSTem")
  val SENSe = Mnemonic("SENSe")
}



trait Keithley6517Current extends SCPICompliantDevice {
  def getInputZeroCheck(): Future[Boolean]
  def setInputZeroCheck(v: Boolean): Future[Boolean]

  def getInputCurrRange(): Future[Double]
  def setInputCurrRange(v: Double): Future[Double]

  def getInputCurrData(): Future[(Double, String)]
  def getInputCurrSensed(): Future[Double]
  def getInputCurrState(): Future[String]
}


object Keithley6517Current extends DeviceCompanion[Keithley6517Current] {
  def impl = { case uri => new Keithley6517CurrentImpl(uri.toString) }
}


// Currently *must* be run over a VXI-11 GPIB connection, instrument
// behaves differently on a serial interface, also SCPI stream decoding
// is currently unavailable due to "#" characters in instrument
// responses

class Keithley6517CurrentImpl(busURI: String) extends Keithley6517Current
  with SCPICompliantDeviceImpl
{
  import Keithley6517._

  import daqcore.defaults.defaultTimeout //!! get actor default timeout somehow?

  val io = ByteStreamIO(busURI, "io")

  // Can't use msgIO.recv due to "#" characters in instrument responses:
  override def rawQry(req: ByteString) = { msgIO.send(req); io.recv() } 

  protected def initDevice() {
    qry(~ABORt!, ESR?).get
    assert { (qry(~SYSTem~ZCHeck!(1), ~CONFigure~CURRent~DC!, ESR?) map respNR1Int get) == 0 }
  }
  initDevice()
  
  def getInputZeroCheck(): Future[Boolean] = qry(~SYSTem~ZCHeck?) map respNR1Boolean
  def setInputZeroCheck(v: Boolean) = qry(~SYSTem~ZCHeck!(if (v) 1 else 0), ~SYSTem~ZCHeck?) map respNR1Boolean

  def getInputCurrRange() = qry(~SENSe~CURRent~RANGe?) map respNRfDouble
  def setInputCurrRange(v: Double) = qry(~SENSe~CURRent~RANGe!(v), ~SENSe~CURRent~RANGe?) map respNRfDouble

  def getInputCurrData() = qry(~READ?) map { case Response(Result(AARD(currentReadingFmt(v, s)))) => (v.toDouble, s) }
  def getInputCurrSensed() = getInputCurrData() map { case (v,s) => v}
  def getInputCurrState() = getInputCurrData() map { case (v,s) => s}
}
