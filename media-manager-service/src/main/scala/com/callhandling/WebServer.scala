package com.callhandling

import java.io.File
import java.nio.file.Files

import akka.actor.{Actor, ActorSystem}
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import akka.util.Timeout
import com.callhandling.actors._
import com.callhandling.media.converters.OutputArgs
import com.callhandling.media.io.instances.FileStreamIO
import com.callhandling.media.processor.AudioProcessor
import com.callhandling.media.processor.AudioProcessor.StartConversion

import scala.concurrent.duration._

object WebServer {
  def main(args: Array[String]) {

    implicit val system: ActorSystem = ActorSystem("media-manager-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeout: Timeout = 2.seconds

    //val fileManagerRegion = shardRegion(system, FileActor.props)
    //Service(fileManagerRegion).restart()

    val fileId = "foo.mp3"
    val storagePath = "/home/melvic/Music"
    val fileStreamIO = new FileStreamIO(storagePath)
    val formats = "wav" :: "flv" :: Nil

    val outputArgsSet = formats.map(OutputArgs("sample", _))
    val region = shardRegion(system, AudioProcessor.props(
      id = fileId,
      outputArgsSet = outputArgsSet,
      input = fileStreamIO,
      output = fileStreamIO,
      ackActorRef = TestProbe().ref))
    region ! SendToEntity(fileId, StartConversion(true))
  }
}
