package com.callhandling

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives.{entity, _}
import akka.http.scaladsl.server.{Directive1, RejectionHandler}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.{Timeout}
import com.callhandling.Forms.{ConvertFileForm, UploadFileForm}
import com.callhandling.actors.{FileActor, StreamActor}
import com.callhandling.media.Converter.OutputDetails
import com.callhandling.media.DataType.Rational
import com.callhandling.media.Formats.Format
import com.callhandling.media.StreamDetails
import com.callhandling.media.StreamDetails._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Service {

  final case class FileIdResult(fileId: String)
  final case class UploadResult(
      fileId: String,
      filename: String,
      description: String,
      streams: List[StreamDetails],
      outputFormats: List[Format])

  final case class ConversionResult(message: String, fileId: String, outputDetails: OutputDetails)

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

    implicit val fileIdResultFormat: RJF[FileIdResult] = jsonFormat1(FileIdResult)
    implicit val uploadResultFormat: RJF[UploadResult] = jsonFormat5(UploadResult)
    implicit val conversionResultFormat: RJF[ConversionResult] = jsonFormat3(ConversionResult)

    implicit val uploadFileFormFormat: RJF[UploadFileForm] = jsonFormat1(UploadFileForm)
    implicit val convertFileFormFormat: RJF[ConvertFileForm] = jsonFormat2(ConvertFileForm)

    implicit val validatedFieldFormat = jsonFormat2(FieldErrorInfo)
    def validateForm[T](form: T)(implicit validator: Validator[T]): Directive1[T] = {
      validator(form) match {
        case Nil => provide(form)
        case errors: Seq[FieldErrorInfo] => reject(FormValidationRejection(errors))
      }
    }
  }

  final case class MediaFileDetail(description: String)

  def apply(fileManagerRegion: ActorRef)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer,
      timeout: Timeout) =
    new Service(fileManagerRegion)
}

class Service(fileManagerRegion: ActorRef) (
    implicit system: ActorSystem,
    materializer: ActorMaterializer,
    timeout: Timeout) {
  import FileActor._
  import Forms._
  import Service._
  import JsonSupport._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  implicit val uploadFileFormValidator = UploadFileFormValidator
  implicit val convertFileFormValidator = ConvertFileFormValidator
  implicit val fileIdFormValidator = FileIdFormValidator

  implicit def formValidationRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case FormValidationRejection(invalidFields) =>
        complete(invalidFields)
      }
      .result()

  def uploadV1 = pathPrefix("upload") {
    pathEndOrSingleSlash {
      post {
        entity(as[UploadFileForm]) { form =>
          validateForm(form).apply {
            vform =>
              val fileId = FileActor.generateId
              val fileDataF = fileManagerRegion ? EntityMessage(fileId, SetFormDetails(fileId, vform))
              onSuccess(fileDataF) {
                case FileData(id, _, _, _, _) =>
                  complete(FileIdResult(id))
                case _ => complete(internalError("Could not retrieve file data."))
              }
          }
        }
      }
    } ~
    withoutSizeLimit {
      path(Remaining) { fileId =>
        put {
          val form = FileIdForm(fileId)
          validateForm(form).apply {
            vform => {
              //TODO: Add checking for valid vform.fileId on fileListActor
              if (false) {
                complete(internalError("No actor found with id: " + vform.fileId))
              } else {
                fileUpload("file") {
                  case (metadata, byteSource) =>
                    val fileSink = StreamActor.createSink(
                      system, fileManagerRegion, fileId, metadata.fileName)
                    byteSource.runWith(fileSink)

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
        }
      }
    }
  }

  def convertV1 = pathPrefix("convert") {
    pathEndOrSingleSlash {
      post {
        entity(as[ConvertFileForm]) { form =>
          validateForm(form).apply {
            vform =>
              val outputDetails = OutputDetails("converted", vform.format)
              val conversionF = fileManagerRegion ? EntityMessage(vform.fileId, RequestForConversion(outputDetails))

              onSuccess(conversionF) {
                case ConversionStarted(Left(errorMessage)) => complete(internalError(errorMessage))
                case ConversionStarted(Right(newFileId)) =>
                  complete(ConversionResult("Conversion Started", newFileId, outputDetails))
              }
          }
        }
      }
    } ~
    path("status" / Remaining) { fileId =>
      get {
        val form = FileIdForm(fileId)
        validateForm(form).apply {
          vform =>
            // TODO: conversion status
            complete(fileId)
        }
      }
    }
  }

  def playV1 = pathPrefix("play") {
    path(Remaining) { fileId =>
      get {
        val form = FileIdForm(fileId)
        validateForm(form).apply {
          vform =>
//            val streamActorF = ??? //TODO: replaced with fileManagerRegion ? EntityMessage(fileId, PlayFile)
//            onSuccess(streamActorF) {
//              case byteString: ByteString =>
//                val entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, Source.single(byteString))
//                complete(HttpResponse(entity = entity))
//            }
            complete(fileId)
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
