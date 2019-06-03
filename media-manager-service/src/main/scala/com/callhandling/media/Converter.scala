package com.callhandling.media

import java.io.InputStream
import java.nio.file.{Path, Paths}

import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, FFmpegProgress, PipeInput, ProgressListener, UrlOutput}
import org.apache.tika.Tika

object Converter {
  case class OutputDetails(filename: String, format: String)

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

  def convertFile(fileId: String, inputStream: InputStream): OutputDetails => Unit = {
    case OutputDetails(_, format) =>
      val homeDir = System.getProperty("user.home")
      val outputPath = Paths.get(s"$homeDir/$fileId/$format")

      FFmpeg.atPath(FFmpegConf.Bin)
        .addInput(PipeInput.pumpFrom(inputStream))
        .addOutput(UrlOutput.toPath(outputPath)
          .setFormat(format))
        .execute()
  }
}

