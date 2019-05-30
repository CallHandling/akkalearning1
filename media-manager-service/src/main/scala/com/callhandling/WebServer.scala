package com.callhandling

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.callhandling.actors.FileActor
import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

object WebServer {
  def main(args: Array[String]) {

    implicit val system: ActorSystem = ActorSystem("media-manager-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = 5.seconds
    val fileManagerRegion = FileActor.shardRegion(system)
    Service(fileManagerRegion).restart()
  }
}
