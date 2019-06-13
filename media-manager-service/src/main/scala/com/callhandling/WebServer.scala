package com.callhandling

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, File, FileInputStream}
import java.net.URL

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.callhandling.actors.FileActor
import com.github.kokorin.jaffree.{LogLevel, StreamType}
import com.github.kokorin.jaffree.ffmpeg.UrlOutput
import com.github.kokorin.jaffree.ffprobe.FFprobe

import scala.concurrent.duration._

object WebServer {
  def main(args: Array[String]) {
    import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
    import java.nio.file.{Files, Paths}

    import com.callhandling.media.FFmpegConf
    import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, PipeInput, PipeOutput, UrlInput}

    val inputPath = Paths.get("/home/melvic/Music/foo.mp3")
    val result = FFprobe.atPath(FFmpegConf.Bin)
      .setInput("http://www.rapconverter.com/SampleDownload/Sample1280.avi")
      .setShowStreams(true)
      .execute()

    result.getStreams.forEach { stream =>
      println(stream.getCodecName, stream.getBitRate)
    }

    /*
    val uuid = java.util.UUID.randomUUID().toString
    val inputPath = new File(s"${FFmpegConf.StorageDir}/$uuid").toPath
    val location = "Videos"
    val filename = "bar"
    val data = Files.readAllBytes(Paths.get(s"/home/melvic/$location/$filename.avi"))

    val outputStream = new ByteArrayOutputStream

    FFmpeg.atPath(FFmpegConf.Bin)
      //.addInput(PipeInput.pumpFrom(new URL("http://www.rapconverter.com/SampleDownload/Sample1280.avi").openStream())) //PipeInput.pumpFrom(new DataInputStream(new FileInputStream("/home/melvic/Videos/foo.mp4"))))
      .addInput(PipeInput.pumpFrom(new ByteArrayInputStream(data)))
      //.addOutput(PipeOutput.pumpTo(outputStream)
      .addOutput(UrlOutput.toPath(Paths.get(s"/home/melvic/$location/$filename.wav"))
        .setFormat("wav")
        .addArguments("-c", "copy")
        .addArguments("-map", "0:a"))
      .setProgressListener(progress => {
        println("Converting... " + progress.getTimeMillis)
      })
      .execute()*/

    /*implicit val system: ActorSystem = ActorSystem("media-manager-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeout: Timeout = 2.seconds

    val fileManagerRegion = FileActor.shardRegion(system)
    Service(fileManagerRegion).restart()*/
  }
}
