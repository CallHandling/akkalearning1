package com.callhandling

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{HttpResponse, Multipart}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.callhandling.actors.FileActor
import com.callhandling.actors.StreamActor.{Ack, StreamCompleted, StreamFailure, StreamInitialized}
import com.callhandling.media.DataType.Rational
import com.callhandling.media.Formats.Format
import com.callhandling.media.StreamDetails
import com.callhandling.media.StreamDetails._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.io.StdIn

object Service {
  final case class UploadResult(
      fileId: String,
      filename: String,
      description: String,
      streams: List[StreamDetails],
      outputFormats: List[Format])

  object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    type RJF[A] = RootJsonFormat[A]

    implicit val rationalFormat: RJF[Rational] = jsonFormat2(Rational)
    implicit val codecFormat: RJF[Codec] = jsonFormat5(Codec)
    implicit val aspectRationFormat: RJF[AspectRatio] = jsonFormat2(AspectRatio)
    implicit val colorFormat: RJF[Color] = jsonFormat4(Color)
    implicit val dimensionsFormat: RJF[Dimensions] = jsonFormat2(Dimensions)
    implicit val bitsFormat: RJF[Bits] = jsonFormat4(Bits)
    implicit val nbFormat: RJF[Nb] = jsonFormat3(Nb)
    implicit val samplesFormat: RJF[Samples] = jsonFormat2(Samples)
    implicit val frameRatesFormat: RJF[FrameRates] = jsonFormat2(FrameRates)
    implicit val timeFormat: RJF[Time] = jsonFormat6(Time)
    implicit val channelFormat: RJF[Channel] = jsonFormat2(Channel)
    implicit val streamDetailsFormat: RJF[StreamDetails] = jsonFormat21(StreamDetails.apply)
    implicit val fileFormatFormat: RJF[Format] = jsonFormat2(Format)
    implicit val uploadResultFormat: RJF[UploadResult] = jsonFormat5(UploadResult)
  }

  def apply(fileManagerRegion: ActorRef)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer,
      timeout: Timeout) =
    new Service(fileManagerRegion)
}

class Service(fileManagerRegion: ActorRef)(
    implicit system: ActorSystem,
    materializer: ActorMaterializer,
    timeout: Timeout) {
  import FileActor._
  import Service._
  import JsonSupport._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  def uploadRoute = path("fileUpload") {
    post {
      val fileId = java.util.UUID.randomUUID().toString
      val streamActorF = fileManagerRegion ? EntityMessage(fileId, SetUpStream)
      val streamActor = Await.result(streamActorF, timeout.duration).asInstanceOf[ActorRef]

      lazy val fileSink = Sink.actorRefWithAck(
        streamActor,
        onInitMessage = StreamInitialized,
        ackMessage = Ack,
        onCompleteMessage = StreamCompleted,
        onFailureMessage = StreamFailure)

      entity(as[Multipart.FormData]) { formData =>
        val futureParts: Future[Map[String, String]] = formData.parts.mapAsync[(String, String)](1) {
          case part: BodyPart if part.filename.isDefined && part.name == "file" =>
            part.entity.dataBytes.runWith(fileSink)
            Future.successful("filename" -> part.filename.get)
          case part: BodyPart if part.name == "description" =>
            part.toStrict(2.seconds).map(strict => part.name -> strict.entity.data.utf8String)
        }.runFold(Map.empty[String, String])(_ + _)

        onSuccess(futureParts) { details =>
          fileManagerRegion ! EntityMessage(
            fileId, SetDetails(
              id = fileId,
              details = Details(
                filename = details("filename"),
                description = details("description"))))
          val fileDataF = fileManagerRegion ? EntityMessage(fileId, GetFileData)
          onSuccess(fileDataF) {
            case FileData(id, Details(filename, description), streams, outputFormats) =>
              complete(UploadResult(id, filename, description, streams, outputFormats))
            case _ => complete(HttpResponse(InternalServerError, entity = "Could not retrieve file data"))
          }
        }
      }
    }
  }

  /*
  def convertRoute = path("convertFile") {
    formFields('fileId, 'format.as[Format]) { (fieldId, format) =>

    }
  }*/

  def restart(): Unit = {
    val route = uploadRoute
    val port = 8080
    val bindingFuture = Http().bindAndHandle(uploadRoute, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
