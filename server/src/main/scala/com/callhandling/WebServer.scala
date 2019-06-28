package com.callhandling

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.RejectionHandler
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.callhandling.actors._
import com.callhandling.media.io.instances.FileStreamIO
import com.callhandling.media.processor.AudioProcessor
import com.callhandling.web.validators.Validator.FormValidationRejection

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.io.StdIn

import com.callhandling.web.JsonSupport._

object WebServer {

  def main(args: Array[String]) {
    implicit val system: ActorSystem = ActorSystem("media-manager-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContext= system.dispatcher

    implicit def formValidationRejectionHandler: RejectionHandler =
      RejectionHandler.newBuilder()
        .handle { case FormValidationRejection(invalidFields) =>
          complete(invalidFields)
        }
        .result()

    val route = WebServer.route(system, materializer)
    val port = 8080
    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  def route(implicit system: ActorSystem, materializer: ActorMaterializer) = {
    implicit val timeout: Timeout = 2.seconds

    val fileRegion = shardRegion(
      system = system,
      name = FileActor.RegionName,
      props = FileActor.props(system))

    // TODO: Make this instance configurable
    //  (e.g. different instance per development stage)
    val fileStreamIO = new FileStreamIO(
      // TODO: Make this path configurable
      s"${System.getProperty("user.home")}/akkalearning")

    val audioProcessorRegion = shardRegion(
      system = system,
      name = AudioProcessor.RegionName,
      props = AudioProcessor.props(
        input = fileStreamIO,
        output = fileStreamIO))

    Service(
      fileRegion = fileRegion,
      audioProcessorRegion = audioProcessorRegion,
      input = fileStreamIO,
      output = fileStreamIO).route
  }

}
