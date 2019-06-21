package com.callhandling

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import akka.util.Timeout
import com.callhandling.actors._
import com.callhandling.media.io.instances.FileStreamIO
import com.callhandling.media.processor.AudioProcessor

import scala.concurrent.duration._

object WebServer {
  def main(args: Array[String]) {
    implicit val system: ActorSystem = ActorSystem("media-manager-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeout: Timeout = 2.seconds

    val fileRegion = shardRegion(system, FileActor.props(system))

    // TODO: Make this instance configurable
    //  (e.g. different instance per development stage)
    val fileStreamIO = new FileStreamIO(
      // TODO: Make this path configurable
      s"${System.getProperty("user.home")}/akkalearning")

    val audioProcessorRegion = shardRegion(
      system,
      AudioProcessor.props(
        input = fileStreamIO,
        output = fileStreamIO))

    Service(
      fileRegion = fileRegion,
      audioProcessorRegion = audioProcessorRegion,
      input = fileStreamIO,
      output = fileStreamIO).restart()
  }
}
