package com.callhandling

import java.nio.file.Paths

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{HttpResponse, Multipart}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.callhandling.actors.FileActor
import com.callhandling.actors.StreamActor.{Ack, StreamCompleted, StreamFailure, StreamInitialized}
import com.callhandling.media.Converter.OutputDetails
import com.callhandling.media.Formats.Format
import com.callhandling.media.StreamDetails

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.io.StdIn
import akka.http.scaladsl.server.Directives._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import com.callhandling.media.DataType.Rational
import com.callhandling.media.StreamDetails._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.Unmarshal

object Service {
  final case class UploadResult(
      fileId: String,
      filename: String,
      description: String,
      streams: List[StreamDetails],
      outputFormats: List[Format])

  final case class ConversionResult(message: String, fileId: String, outputDetails: OutputDetails)

  final case class UploadFileForm(description: String)
  case object UploadFileFormConstant {
    val File = "file"
    val Filename = "file"
    val Json = "json"
  }
  final case class ConvertFileForm(fileId: String, format: String)

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
    implicit val outputDetailsFormat: RJF[OutputDetails] = jsonFormat2(OutputDetails)

    implicit val uploadResultFormat: RJF[UploadResult] = jsonFormat5(UploadResult)
    implicit val conversionResultFormat: RJF[ConversionResult] = jsonFormat3(ConversionResult)

    implicit val uploadFileFormFormat: RJF[UploadFileForm] = jsonFormat1(UploadFileForm)
    implicit val convertFileFormFormat: RJF[ConvertFileForm] = jsonFormat2(ConvertFileForm)
  }

  final case class MediaFileDetail(description: String)

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
      val fileId = FileActor.generateId
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
          case part: BodyPart if part.filename.isDefined && part.name == UploadFileFormConstant.File =>
            part.entity.dataBytes.runWith(fileSink)
            Future.successful(UploadFileFormConstant.Filename -> part.filename.get)
          case part: BodyPart if part.name == UploadFileFormConstant.Json =>
            part.toStrict(2.seconds).map(strict => part.name -> strict.entity.data.utf8String)
        }.runFold(Map.empty[String, String])(_ + _)

        onSuccess(futureParts) { details => {
          val form =  Unmarshal(details(UploadFileFormConstant.Json)).to[UploadFileForm]
          fileManagerRegion ! EntityMessage(
            fileId, SetDetails(
              id = fileId,
              details = Details(
                filename = details(UploadFileFormConstant.Filename),
                description = form.value.map(f => f.get.description).getOrElse(""))))
          }

          val fileDataF = fileManagerRegion ? EntityMessage(fileId, GetFileData)
          onSuccess(fileDataF) {
            case FileData(id, Details(filename, description), streams, outputFormats, _) =>
              complete(UploadResult(id, filename, description, streams, outputFormats))
            case _ => complete(internalError("Could not retrieve file data."))
          }
        }
      }
    }
  }

  def convertRoute = path("convertFile") {
    post {
      entity(as[ConvertFileForm]) { form =>
        val outputDetails = OutputDetails("converted", form.format)
        val conversionF = fileManagerRegion ? EntityMessage(form.fileId, ConvertFile(form.fileId, outputDetails))

        onSuccess(conversionF) {
          case ConversionStarted(Left(errorMessage)) => complete(internalError(errorMessage))
          case ConversionStarted(Right(newFileId)) =>
            complete(ConversionResult("Conversion Started", newFileId, outputDetails))
        }
      }
    }
  }

  def internalError(msg: String) = HttpResponse(InternalServerError, entity = msg)

  def restart(): Unit = {
    val route = uploadRoute ~ convertRoute
    val port = 8080
    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
