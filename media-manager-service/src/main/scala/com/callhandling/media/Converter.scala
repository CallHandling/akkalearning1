package com.callhandling.media

import java.nio.file.Path

import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, UrlOutput}
import org.apache.tika.Tika

object Converter {
  case class OutputDetails(outputPath: Path, format: String)

  def getOutputFormats(data: Array[Byte]) = {
    val mimeType = mimeTypeOf(data)

    if (isAudio(mimeType)) Formats.Audios.all
    else if (isVideo(mimeType)) Formats.Videos.all
    else Nil
  }

  def isAudio: String => Boolean = _.startsWith("audio")
  def isVideo: String => Boolean = _.startsWith("video")
  def isSupportedMimeType: String => Boolean = mime => isAudio(mime) || isVideo(mime)

  def mimeTypeOf: Array[Byte] => String = new Tika().detect

  def convertFile(inputPath: Path): OutputDetails => Path = {
    case OutputDetails(outputPath, format) =>
      val result = FFmpeg.atPath(inputPath)
        .addOutput(UrlOutput.toPath(outputPath)
          .setFormat(format))
        .execute()
      outputPath
  }
}

