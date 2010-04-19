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


package daqcore.profiles

import scala.actors._

import daqcore.util._
import daqcore.actors._


trait MsgReader extends Profile with Closeable {
  def read(timeout: Long = Int.MaxValue): Future[ByteCharSeq] =
    srv.!!& (MsgIO.Read(timeout)) { case x: ByteCharSeq => x }
}


trait MsgWriter extends Profile with Closeable {
  def write(data: Seq[Byte]) : Unit =
    srv ! MsgIO.Write(data)
}


trait MsgIO extends MsgReader with MsgWriter

object MsgIO {
  case class Read(timeout: Long = Int.MaxValue) // Reply: Future[ByteCharSeq]

  case class Write(data: Seq[Byte])
}