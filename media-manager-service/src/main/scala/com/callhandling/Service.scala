package com.callhandling

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.server.Directives.{as, entity, path}
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.callhandling.media.DataType.Rational
import com.callhandling.media.MediaInformation.{AspectRatio, Bits, Channel, Codec, Color, Dimensions, FrameRates, Nb, Samples, Time}
import com.callhandling.actors.FileActor
import com.callhandling.actors.FileActor.{GetMediaInformation, GetOutputFormats, SetDetails}
import com.callhandling.media.Formats.Format
import com.callhandling.media.NonEmptyMediaInformation
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.io.StdIn

object Service {
  final case class UploadResult(id: String, mediaInfo:
    NonEmptyMediaInformation,
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
    implicit val nonEmptyMediaInformationFormat: RJF[NonEmptyMediaInformation] = jsonFormat21(NonEmptyMediaInformation)
    implicit val fileFormatFormat: RJF[Format] = jsonFormat2(Format)
    implicit val uploadResultFormat: RJF[UploadResult] = jsonFormat3(UploadResult)
  }

  def start(): Unit = {
    import JsonSupport._

    implicit val system: ActorSystem = ActorSystem("my-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = 2.seconds

    val fileId = java.util.UUID.randomUUID().toString
    val fileActor = system.actorOf(FileActor.props(fileId), "file-actor")
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
          val mediaInfoF = fileActor ? GetMediaInformation
          onSuccess(mediaInfoF) {
            case info: NonEmptyMediaInformation =>
              onSuccess(fileActor ? GetOutputFormats) {
                case outputFormats: List[Format] => complete(UploadResult(fileId, info, outputFormats))
                case _ => complete("Format can not be process")
              }
            case _ => complete("Media is required")
          }
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
