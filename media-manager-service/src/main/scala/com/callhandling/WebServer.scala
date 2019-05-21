package com.callhandling

import akka.Done
import akka.actor._
import akka.pattern.ask
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.callhandling.actors.FileActor
import com.callhandling.actors.FileActor.{Display, SetDescription, SetFilename}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object WebServer {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val timeout = Timeout(5 seconds)

    val fileActor = system.actorOf(
      FileActor.props(java.util.UUID.randomUUID().toString), "file-actor")
    val FileFieldName = "file"

    val route = path("fileUpload") {
      entity(as[Multipart.FormData]) { formData =>
        val futureParts: Future[Done] = formData.parts.mapAsync(1) {
          case part: BodyPart if part.filename.isDefined && part.name == FileFieldName =>
            fileActor ? SetFilename(part.filename.get)
          case part: BodyPart =>
            part.toStrict(2.seconds).map { strict =>
              val data = strict.entity.data.utf8String

              fileActor ! (part.name match {
                case "description" => SetDescription(data)
              })
            }

            part.name match {
              case "description" => part.toStrict(2.seconds).map { strict =>
                fileActor ! SetDescription(strict.entity.data.utf8String)
              }
            }
        }.runWith(Sink.ignore)

        onSuccess(futureParts) { _ =>
          fileActor ! Display
          complete("ok")
        }
      }
    }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
