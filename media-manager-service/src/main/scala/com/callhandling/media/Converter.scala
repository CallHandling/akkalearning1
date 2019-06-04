package com.callhandling.media

import java.io.InputStream
import java.nio.file.{Path, Paths}
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit

import akka.stream.scaladsl.StreamConverters
import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, FFmpegProgress, PipeInput, ProgressListener, UrlInput, UrlOutput}
import org.apache.tika.Tika

object Converter {
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
      timeMillis: Long)

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

  def convertFile(fileId: String, inputPath: Path, timeDuration: Float): OutputDetails => Unit = {
    case OutputDetails(_, format) =>
      val outputPath = Paths.get(s"${FFmpegConf.StorageDir}/$fileId.$format")

      val progressListener: ProgressListener = { progress =>
        val progressDetails = ProgressDetails(
          bitRate = progress.getBitrate,
          drop = progress.getDrop,
          dup = progress.getDup,
          fps = progress.getFps,
          frame = progress.getFrame,
          q = progress.getQ,
          size = progress.getSize,
          speed = progress.getSpeed,
          timeMillis = progress.getTimeMillis)
        println(s"Progress: $progressDetails")

        // display the progress
        val timeDurationMillis = timeDuration * 1000
        val percent = progressDetails.timeMillis / timeDurationMillis * 100
        println(s"Percent: $percent")
      }

      FFmpeg.atPath(FFmpegConf.Bin)
        .addInput(UrlInput.fromPath(inputPath))
        .addOutput(UrlOutput.toPath(outputPath)
          .setFormat(format))
        .setProgressListener(progressListener)
        .execute()
  }
}

