// Copyright (C) 2012 Oliver Schulz <oliver.schulz@tu-dortmund.de>

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


package daqcore.actors

import akka.actor._

import daqcore.util._


class EventStreamLogger extends Actor with Logging {
  context.system.eventStream.subscribe(self, classOf[UnhandledMessage])
  
  def receive = {
    case msg @ UnhandledMessage(message, sender, self) =>
      log.warn(loggable(msg.toString))

    case msg @ DeadLetter(message, sender, recipient) =>
      log.debug(loggable(msg.toString))
  }
}
