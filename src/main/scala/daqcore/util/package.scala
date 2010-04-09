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


package daqcore


package object util {


implicit def idxSeq2subSeq[T](seq: IndexedSeq[T]) = new SubIdxSeq(seq, 0, seq.length)


def fctResponder[A](x: () => A) = new Responder[A] { def respond(k: A => Unit) = k(x()) }


def classMF(a: Any): ClassManifest[_] = a match {
  case a:Boolean => classManifest[Boolean]
  case a:Byte => classManifest[Byte]
  case a:Char => classManifest[Char]
  case a:Short => classManifest[Short]
  case a:Int => classManifest[Int]
  case a:Long => classManifest[Long]
  case a:Float => classManifest[Float]
  case a:Double => classManifest[Double]
  case a:Unit => classManifest[Unit]
  case a:AnyRef => scala.reflect.ClassManifest.fromClass(a.getClass)
}


def as[A](x:Any) = x.asInstanceOf[A]


}
