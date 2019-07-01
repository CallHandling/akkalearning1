package com.callhandling

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.util.Timeout
import cats.data.EitherT
import cats.data.Validated.{Invalid, Valid}
import cats.instances.option._
import cats.syntax.either._
import com.callhandling.actors.{FileActor, SendToEntity}
import com.callhandling.media.MediaStream
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.converters._
import com.callhandling.media.io.{IOValidation, MediaReader, MediaWriter}
import com.callhandling.web.JsonSupport._
import com.callhandling.web.validators._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn

object Service {
  final case class FileIdResult(fileId: String)
  final case class UploadResult(
      fileId: String,
      filename: String,
      description: String,
      streams: Vector[MediaStream],
      outputFormats: Vector[Format])

  final case class MediaFileDetail(description: String)

  def apply[I: MediaReader, O: MediaWriter](
      fileRegion: ActorRef,
      audioProcessorRegion: ActorRef,
      input: I,
      output: O)
      (implicit system: ActorSystem, mat: ActorMaterializer, timeout: Timeout) =
    new Service(fileRegion, audioProcessorRegion, input, output)

  implicit class ValidationRequestMarshaller[A](um: FromRequestUnmarshaller[A]) {
    def validate(implicit validation: FormValidation[A]) = um.flatMap { _ => _ => entity =>
      validateForm(entity) {
        case Valid(_) => Future.successful(entity)
        case Invalid(failures) =>
          Future.failed(new IllegalArgumentException(
            failures.iterator.map(_.errorMessage).mkString("\n")))
      }
    }
  }
}

class Service[I: MediaReader, O: MediaWriter](
    fileRegion: ActorRef, audioProcessorRegion: ActorRef, input: I, output: O)
    (implicit system: ActorSystem, mat: ActorMaterializer, timeout: Timeout) {
  import FileActor._
  import Service._
  import com.callhandling.web.Form._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  def uploadV1 = pathPrefix("upload") {
    pathEndOrSingleSlash {
      post {
        entity(as[UploadFileForm].validate) { case UploadFileForm(description) =>
          val fileId = FileActor.generateId
          fileRegion ! SendToEntity(fileId, SetId(fileId))
          fileRegion ! SendToEntity(fileId, SetDescription(description))
          complete(FileIdResult(fileId))
        }
      }
    } ~
    withoutSizeLimit {
      path(Remaining) { fileId =>
        put {
          validateForm(FileIdForm(fileId)) {
            case Valid(_) =>
              //TODO: Add checking for valid vform.fileId on fileListActor
              if (false) complete(internalError("Media not found: " + fileId))
              else fileUpload("file") { case (FileInfo(_, filename, _), byteSource) =>
                fileRegion ! SendToEntity(fileId, UploadStarted)

                val uploadFutureOr = for {
                  outlet <- MediaWriter[O].write(output, fileId)
                } yield byteSource.toMat(outlet)(Keep.right).run

                uploadFutureOr match {
                  case Right(uploadF) => onSuccess(uploadF) { _ =>
                    fileRegion ! SendToEntity(fileId, UploadCompleted)
                    fileRegion ! SendToEntity(fileId, SetFilename(filename))

                    val fileDataF = fileRegion ? SendToEntity(fileId, GetDetails)
                    onSuccess(fileDataF) {
                      case Details(_, _, description) =>
                        val streams = MediaReader[I].mediaStreams(input, fileId)
                        val outputFormats = MediaReader[I].outputFormats(input, fileId)
                        complete(UploadResult(fileId, filename, description, streams, outputFormats))
                      case _ => complete(internalError("Could not retrieve file data."))
                    }
                  }
                  case Left(_) => complete(internalError("Unable to write to file"))
                }
              }
            case Invalid(failures) => complete(internalError(
              failures.iterator.map(_.errorMessage).mkString("\n")))
          }
        }
      }
    }
  }

  def convertV1 = pathPrefix("convert") {
    pathEndOrSingleSlash {
      post {
        entity(as[ConvertFileForm].validate) {
          case ConvertFileForm(fileId, format, channels, sampleRate, codec) =>
            val outputArgs = OutputArgs(format, channels, sampleRate, codec)

            val validFormat = MediaReader[I].outputFormats(input, fileId).exists(_.code == format)
            if (validFormat) {
              fileRegion ! SendToEntity(
                fileId, RequestForConversion(Vector(outputArgs)))
              complete("Conversion Started")
            } else complete(internalError("Invalid format"))
        }
      }
    } ~
    path("status" / Remaining) { fileId =>
      get {
        entity(as[FormatForm].validate) { case FormatForm(format) =>
          val conversionStatusF = fileRegion ? SendToEntity(fileId, GetConversionStatus(format))

          onSuccess(conversionStatusF) {
            case progress: OnGoing => complete(progress)
            case NoProgress => complete("No progress available")
            case Completed => complete("Conversion completed")
            case result => complete(
              internalError(s"Could not retrieve conversion status for $format: $result"))
          }
        }
      }
    }
  }

  def playV1 = pathPrefix("play") {
    path(Remaining) { fileId =>
      get {
        entity(as[OptionalFormatForm].validate) { case OptionalFormatForm(formatOpt) =>
          val inletOpt = formatOpt
            .map(MediaReader[I].read(input, fileId, _))
            .orElse(Some(MediaReader[I].read(input, fileId)))

          val successOr = for {
            inlet <- EitherT(inletOpt)
          } yield complete(HttpEntity(ContentTypes.`application/octet-stream`, inlet))

          successOr.value.get.valueOr {
            error: IOValidation => complete(internalError(error.errorMessage))
          }
        }
      }
    }
  }

  def internalError(msg: String) = HttpResponse(InternalServerError, entity = msg)

  def restart(): Unit = {
    val route = pathPrefix("api" / "v1" / "file") { uploadV1 ~ convertV1 ~ playV1}
    val port = 8080
    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
