package com.callhandling.media.converters

import java.nio.file.Path

import org.apache.tika.Tika

object Converter {
  case class OutputArgs(filename: String, format: String)
  case class Progress(
      bitRate: Double,
      drop: Long,
      dup: Long,
      fps: Double,
      frame: Long,
      q: Double,
      size: Long,
      speed: Double,
      timeMillis: Long,
      percent: Float) {
    override def toString = {
      val percentTwoDecimal = math.floor(percent * 100) / 100
      s"$percentTwoDecimal% - ${super.toString}"
    }
  }
  case object EmptyProgress
}
