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
    implicit val system: ActorSystem = ActorSystem("media-manager-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeout: Timeout = 2.seconds

    val fileManagerRegion = FileActor.shardRegion(system)
    Service(fileManagerRegion).restart()
  }
}
