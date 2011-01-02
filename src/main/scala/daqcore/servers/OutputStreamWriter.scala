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

import java.io.{File, OutputStream, FileOutputStream}

import akka.actor.Actor.actorOf
import akka.config.Supervision.{LifeCycle, Temporary}

import daqcore.actors._
import daqcore.profiles._
import daqcore.util._


class OutputStreamWriter(output: OutputStream) extends CloseableServer {
  override def profiles = super.profiles.+[ByteStreamOutput]

  override def init() = {
    super.init()
    self. lifeCycle = Temporary // Restart strategy unclear - how to handle input?
    atCleanup { output.flush(); output.close() }
  }

  
  protected def srvSend(data: ByteSeq): Unit = {
    output.write(data.toArray)
  }

  
  protected def srvFlush(): Unit = {
    output.flush()
  }
 
  override def serve = super.serve orElse {
    case ByteStreamOutput.Send(data) => srvSend(data)
    case ByteStreamOutput.Flush() => srvFlush()
  }
}


object OutputStreamWriter {
  def apply(output: OutputStream, sv: Supervising = defaultSupervisor): ByteStreamOutput =
    new ServerProxy(sv.linkStart(actorOf(new OutputStreamWriter(output)))) with ByteStreamOutput
}
