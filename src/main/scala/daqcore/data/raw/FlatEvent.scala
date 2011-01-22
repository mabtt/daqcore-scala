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


package daqcore.data
package raw

import java.util.UUID

import daqcore.util._


case class FlatEvent (
  idx: Int,
  run: UUID,
  time: Double,
  systime: Double,
  trig: Seq[Int],
  trans_trigPos: Seq[Int],
  trans_samples_ch: Seq[Int],
  trans_samples_val_n: Seq[Int],
  trans_samples_val: ArrayVec[Short]
) {
  def toEvent: Event = {
    val channelIt = trans_samples_ch.iterator
    val trigPosIt = trans_trigPos.iterator
    val nSamplesIt = trans_samples_val_n.iterator

    var transients = Map.empty[Int, raw.Transient]
    var offset = 0

    while(channelIt.hasNext) {
      val channel = channelIt.next
      val trigPos = trigPosIt.next
      val nSamples = nSamplesIt.next
      val samples = (for {v <- trans_samples_val.view.slice(offset, offset + nSamples)} yield v.toInt).toArrayVec
      transients = transients + (channel -> raw.Transient(trigPos, samples))
      offset = offset + nSamples
    }

    Event(
      idx = idx,
      run = run,
      time = time,
      systime = systime,
      trig = trig,
      trans = transients
    )
  }
}


object FlatEvent {
  def apply(event: Event): FlatEvent = {
    val transCh = event.trans.keys.toSeq.sortWith{_ < _}

    FlatEvent(
      run = event.run,
      idx = event.idx,
      time = event.time,
      systime = event.systime,
      trig = event.trig,
      trans_samples_ch = for (ch <- transCh) yield ch,
      trans_trigPos = for (ch <- transCh) yield event.trans(ch).trigPos,
      trans_samples_val_n = for (ch <- transCh) yield event.trans(ch).samples.size,
      trans_samples_val = (for {ch <- transCh.view; s <-event.trans(ch).samples.view} yield s.toShort).toArrayVec
    )
  }
}
