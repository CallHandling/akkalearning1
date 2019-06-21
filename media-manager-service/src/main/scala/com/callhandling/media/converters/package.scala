package com.callhandling.media

import java.io.{InputStream, OutputStream}

import com.callhandling.media.converters.Progress.OnGoing
import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, PipeInput, PipeOutput, ProgressListener}

package object converters {
  case class OutputArgs(
      format: String,
      channels: Int,
      sampleRate: Int,
      codec: String)

  implicit class InputStreamConverter(inputStream: InputStream) {
    def convert(outputStream: OutputStream, timeDuration: Float, outputArgs: OutputArgs)
      (f: OnGoing => Unit): Option[ConversionError] = outputArgs match {
      case OutputArgs(format, channels, sampleRate, codec) =>
        val progressListener: ProgressListener = { progress =>
          val timeDurationMillis = timeDuration * 1000
          val percent = progress.getTimeMillis / timeDurationMillis * 100

          val progressDetails = OnGoing(
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

        // TODO: Catch the the library exceptions so we can return it back
        FFmpeg.atPath(FFmpegConf.Bin)
          .addInput(PipeInput.pumpFrom(inputStream))
          .addOutput(PipeOutput.pumpTo(outputStream)
            .addArguments("-ar", sampleRate.toString)
            .addArguments("-ac", channels.toString)
            .addArguments("-c:a", codec)
            .addArguments("-f", format))
          .setProgressListener(progressListener)
          .execute()

        None
    }
  }
}

