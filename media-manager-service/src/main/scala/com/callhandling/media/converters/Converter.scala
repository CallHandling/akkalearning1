package com.callhandling.media.converters

import java.nio.file.Path

import com.callhandling.media.Formats
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

  def getOutputFormats(data: Array[Byte]) = {
    val mimeType = mimeTypeOf(data)

    if (isAudio(mimeType)) Formats.Audios.all
    else if (isVideo(mimeType)) Formats.Videos.all
    else Nil
  }

  def getOutputFormats(path: Path): List[Formats.Format] = {
    val mimeType = new Tika().detect(path)
    getOutputFormats(mimeType)
  }

  def getOutputFormats(mimeType: String): List[Formats.Format] = {
    if (isAudio(mimeType)) Formats.Audios.all
    else if (isVideo(mimeType)) Formats.Videos.all
    else Nil
  }

  def isAudio: MimeDetector = _.startsWith("audio")
  def isVideo: MimeDetector = _.startsWith("video")
  def isSupportedMimeType: MimeDetector = mime => isAudio(mime) || isVideo(mime)

  def mimeTypeOf: Array[Byte] => String = new Tika().detect
}
