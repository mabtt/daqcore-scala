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


package daqcore.util

import java.nio.{ByteOrder => NIOByteOrder, ByteBuffer => NIOByteBuffer}


// Protobuf-style variable length encoding
sealed trait PBVarLenValEnc extends ValEncoding {
  override def putFloat(target: GenericByteSeqBuilder, x: Float): Unit =
    BigEndian.putFloat(target, x)
  
  override def putDouble(target: GenericByteSeqBuilder, x: Double): Unit =
    BigEndian.putDouble(target, x)

  override def getFloat(source: GenericByteSeqIterator): Float =
    BigEndian.getFloat(source) 

  override def getDouble(source: GenericByteSeqIterator): Double =
    BigEndian.getDouble(source) 

  override def putFloats(target: GenericByteSeqBuilder, xs: ArrayVec[Float]): Unit =
    BigEndian.putFloats(target, xs)
  
  override def putDoubles(target: GenericByteSeqBuilder, xs: ArrayVec[Double]): Unit =
    BigEndian.putDoubles(target, xs)

  override def getFloats(source: GenericByteSeqIterator, length: Int): ArrayVec[Float] = 
    BigEndian.getFloats(source, length) 
  
  override def getDoubles(source: GenericByteSeqIterator, length: Int): ArrayVec[Double] = 
    BigEndian.getDoubles(source, length) 
}


// Protobuf-style variable length encoding (for unsigned values)
case object PBUnsignedVLEnc extends PBVarLenValEnc {
  def putByte(target: GenericByteSeqBuilder, x: Byte) =
    putInt(target, x.toInt)

  def putShort(target: GenericByteSeqBuilder, x: Short) =
    putLong(target, x.toInt)

  def putInt(target: GenericByteSeqBuilder, x: Int) =
    putLong(target, x.toInt)

  def putLong(target: GenericByteSeqBuilder, x: Long) = {
    var rest = x
    do {
      val newRest = rest >>> 7
      if (newRest == 0) target += (rest & 0x7F).toByte
      else target += ((rest & 0x7F) | 0x80).toByte
      rest = newRest
    } while (rest != 0)
  }

  def getByte(source: GenericByteSeqIterator) = {
    val x = getLong(source)
    if ((x < Byte.MinValue) || (x > Byte.MaxValue))
      throw new IllegalArgumentException("Overflow while reading variable-length Byte")
    else x.toByte
  }
  
  def getShort(source: GenericByteSeqIterator) = {
    val x = getLong(source)
    if ((x < Short.MinValue) || (x > Short.MaxValue))
      throw new IllegalArgumentException("Overflow while reading variable-length Short")
    else x.toShort
  }

  def getInt(source: GenericByteSeqIterator) = {
    val x = getLong(source)
    if ((x < Int.MinValue) || (x > Int.MaxValue))
      throw new IllegalArgumentException("Overflow while reading variable-length Short")
    else x.toInt
  }

  def getLong(source: GenericByteSeqIterator) = {
    var outputPos = 0;
    var v = 0; var pos = 0; var finished = false
    while (!finished) {
      if (pos >= 64) throw new IllegalArgumentException("Overflow while reading variable-length Long")
      val b = source.next()
      v = v | ((b & 0x7f) << pos)
      if ((b & 0x80) == 0) finished = true
      else pos += 7
    }
    v
  }
}


// Protobuf-style zig-zig variable length encoding (for signed values)
case object PBSignedVLEnc extends PBVarLenValEnc {
  import daqcore.math.ZigZagEnc


  def putByte(target: GenericByteSeqBuilder, x: Byte) = 
    PBUnsignedVLEnc.putByte(target, ZigZagEnc.encode(x))

  def putShort(target: GenericByteSeqBuilder, x: Short) =
    PBUnsignedVLEnc.putShort(target, ZigZagEnc.encode(x))

  def putInt(target: GenericByteSeqBuilder, x: Int) =
    PBUnsignedVLEnc.putInt(target, ZigZagEnc.encode(x))

  def putLong(target: GenericByteSeqBuilder, x: Long) =
    PBUnsignedVLEnc.putLong(target, ZigZagEnc.encode(x))


  def getByte(source: GenericByteSeqIterator) =
     ZigZagEnc.decode(PBUnsignedVLEnc.getByte(source))
 
  def getShort(source: GenericByteSeqIterator) =
     ZigZagEnc.decode(PBUnsignedVLEnc.getShort(source))

  def getInt(source: GenericByteSeqIterator) =
     ZigZagEnc.decode(PBUnsignedVLEnc.getInt(source))

  def getLong(source: GenericByteSeqIterator) =
     ZigZagEnc.decode(PBUnsignedVLEnc.getLong(source))
}
