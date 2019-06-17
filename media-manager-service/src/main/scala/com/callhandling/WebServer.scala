package com.callhandling

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.callhandling.actors._

import scala.concurrent.duration._

object WebServer {
  def main(args: Array[String]) {
    implicit val system: ActorSystem = ActorSystem("media-manager-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeout: Timeout = 2.seconds

    val fileManagerRegion = shardRegion(system, FileActor.props)
    Service(fileManagerRegion).restart()
  }
}
