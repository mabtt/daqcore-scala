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


package daqcore.actors


case object Timeout



sealed abstract class TimeoutSpec {
  def isDefined: Boolean
  def get: Long
  def max(that: TimeoutSpec): TimeoutSpec
  def min(that: TimeoutSpec): TimeoutSpec
}


case class SomeTimeout(ms: Long) extends TimeoutSpec {
  def isDefined = true
  def get = ms
  
  def max(that: TimeoutSpec) =
    if (that.isDefined) SomeTimeout(this.get max that.get)
    else that

  def min(that: TimeoutSpec) = 
    if (that.isDefined) SomeTimeout(this.get min that.get)
    else this
}


case object NoTimeout extends TimeoutSpec {
  def isDefined = false
  def get = throw new java.util.NoSuchElementException("NoTimeout.get")
  def max(that: TimeoutSpec) = this
  def min(that: TimeoutSpec) = that
}
