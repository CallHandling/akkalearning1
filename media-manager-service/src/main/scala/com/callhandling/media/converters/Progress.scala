package com.callhandling.media.converters

sealed trait Progress

final case class OnGoing(
    bitRate: Double,
    drop: Long,
    dup: Long,
    fps: Double,
    frame: Long,
    q: Double,
    size: Long,
    speed: Double,
    timeMillis: Long,
    percent: Float) extends Progress {
  override def toString = {
    val percentTwoDecimal = math.floor(percent * 100) / 100
    s"$percentTwoDecimal% - ${super.toString}"
  }
}

case object Completed extends Progress
