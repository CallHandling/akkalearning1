package com.callhandling.media

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.file.Paths

import akka.util.ByteString
import com.callhandling.util.FileUtil
import com.github.kokorin.jaffree.ffmpeg._
import org.apache.tika.Tika

object Converter {
  type MimeDetector = String => Boolean

  case class OutputDetails(filename: String, format: String)
  case class ProgressDetails(
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

  def getOutputFormats(data: Array[Byte]) = {
    val mimeType = mimeTypeOf(data)

    if (isAudio(mimeType)) Formats.Audios.all
    else if (isVideo(mimeType)) Formats.Videos.all
    else Nil
  }

  def isAudio: MimeDetector = _.startsWith("audio")
  def isVideo: MimeDetector = _.startsWith("video")
  def isSupportedMimeType: MimeDetector = mime => isAudio(mime) || isVideo(mime)

  def mimeTypeOf: Array[Byte] => String = new Tika().detect

  def convert(bytes: ByteString, timeDuration: Float, outputDetails: OutputDetails)
      (f: ProgressDetails => Unit): ByteString = outputDetails match {
    case OutputDetails(_, format) =>
      val inputPath = FileUtil.writeToTempAndGetPath(bytes)
      val outputStream = new ByteArrayOutputStream

      val progressListener: ProgressListener = { progress =>
        val timeDurationMillis = timeDuration * 1000
        val percent = progress.getTimeMillis / timeDurationMillis * 100

        val progressDetails = ProgressDetails(
          bitRate = progress.getBitrate,
          drop = progress.getDrop,
          dup = progress.getDup,
          fps = progress.getFps,
          frame = progress.getFrame,
          q = progress.getQ,
          size = progress.getSize,
          speed = progress.getSpeed,
          timeMillis = progress.getTimeMillis,
          percent: Float)

        f(progressDetails)
      }

      FFmpeg.atPath(FFmpegConf.Bin)
        .addInput(UrlInput.fromPath(inputPath))
        .addOutput(PipeOutput.pumpTo(outputStream)
          .setFormat(format))
        .setProgressListener(progressListener)
        .execute()

      ByteString(outputStream.toByteArray)
  }
}

