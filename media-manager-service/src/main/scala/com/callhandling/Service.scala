package com.callhandling

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.server.Directives.{as, entity, path}
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.callhandling.actors.FileActor
import com.callhandling.actors.FileActor.SetDetails
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object Service {
  def start(): Unit = {
    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val fileActor = system.actorOf(
      FileActor.props(java.util.UUID.randomUUID().toString), "file-actor")
    val FileFieldName = "file"
    val FormFields = List("description")

    lazy val fileSink = Sink.actorRefWithAck(fileActor,
      onInitMessage = FileActor.StreamInitialized,
      ackMessage = FileActor.Ack,
      onCompleteMessage = FileActor.StreamCompleted,
      onFailureMessage = FileActor.StreamFailure)

    val route = path("fileUpload") {
      entity(as[Multipart.FormData]) { formData =>
        val futureParts: Future[Map[String, String]] = formData.parts.mapAsync[(String, String)](1) {
          case part: BodyPart if part.filename.isDefined && part.name == FileFieldName =>
            part.entity.dataBytes.runWith(fileSink)
            Future.successful("filename" -> part.filename.get)
          case part: BodyPart if FormFields.contains(part.name) =>
            part.toStrict(2.seconds).map(strict => part.name -> strict.entity.data.utf8String)
        }.runFold(Map.empty[String, String])(_ + _)

        onSuccess(futureParts) { details =>
          fileActor ! SetDetails(
            filename = details("filename"),
            description = details("description")
          )
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
