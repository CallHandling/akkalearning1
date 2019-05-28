package com.callhandling

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.callhandling.actors.FileActor
import com.callhandling.actors.FileActor.{FileData, GetFileData, SetDetails, Uploading}
import com.callhandling.media.DataType.Rational
import com.callhandling.media.Formats.Format
import com.callhandling.media.StreamDetails
import com.callhandling.media.StreamDetails._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn

object Service {
  final case class UploadResult(id: String, streams:
    List[StreamDetails],
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
    implicit val uploadResultFormat: RJF[UploadResult] = jsonFormat3(UploadResult)
  }

  def apply() = new Service
}

class Service {
  import Service._
  import JsonSupport._

  implicit val system: ActorSystem = ActorSystem("media-manager-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val timeout: Timeout = 2.seconds

  val fileId = java.util.UUID.randomUUID().toString
  val fileActor = system.actorOf(FileActor.props(fileId), "file-actor")

  lazy val fileSink = Sink.actorRefWithAck(fileActor,
    onInitMessage = FileActor.StreamInitialized,
    ackMessage = FileActor.Ack,
    onCompleteMessage = FileActor.StreamCompleted,
    onFailureMessage = FileActor.StreamFailure)

  def uploadRoute = path("fileUpload") {
    post {
      entity(as[Multipart.FormData]) { formData =>
        val futureParts: Future[Map[String, String]] = formData.parts.mapAsync[(String, String)](1) {
          case part: BodyPart if part.filename.isDefined && part.name == "file" =>
            part.entity.dataBytes.runWith(fileSink)
            Future.successful("filename" -> part.filename.get)
          case part: BodyPart if part.name == "description" =>
            part.toStrict(2.seconds).map(strict => part.name -> strict.entity.data.utf8String)
        }.runFold(Map.empty[String, String])(_ + _)

        onSuccess(futureParts) { details =>
          fileActor ! SetDetails(
            filename = details("filename"),
            description = details("description")
          )

          val fileDataF = fileActor ? GetFileData
          onSuccess(fileDataF) {
            case FileData(_, _, streams, outputFormats) =>
              complete(UploadResult(fileId, streams, outputFormats))
            case _ => complete("Can not retrieve file data.")
          }
        }
      }
    }
  }
  /*
  def convertRoute = path("convertFile") {
    formFields('fileId, 'format) { (fieldId, format) =>

    }
  }*/

  def restart(): Unit = {
    val route = uploadRoute

    val bindingFuture = Http().bindAndHandle(uploadRoute, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
