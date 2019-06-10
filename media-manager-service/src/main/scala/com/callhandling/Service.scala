package com.callhandling

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit

import akka.actor.typed.javadsl.Adapter
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, MediaTypes, Multipart, RequestEntity, StatusCodes}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.{Directive1, RejectionHandler}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.{ByteString, Timeout}
import com.callhandling.Forms._
import com.callhandling.actors.{FileActor, StreamActor}
import com.callhandling.media.Converter.{OutputDetails, ProgressDetails}
import com.callhandling.media.DataType.Rational
import com.callhandling.media.Formats.Format
import com.callhandling.media.{Converter, FFmpegConf, StreamDetails}
import com.callhandling.media.StreamDetails._
import com.callhandling.typed.cluster.ActorSharding
import com.callhandling.typed.persistence.{AddFile, AddFileCommand, AddFormCommand, FileListActor, GetFile, GetFileCommand, UploadedFile}
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat}

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, PipeOutput, UrlInput, UrlOutput}

import scala.concurrent.duration._
import scala.Option

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
    implicit val fileIdFormFormat: RJF[FileIdForm] = jsonFormat1(FileIdForm)
    implicit val conversionProgressFormat: RJF[ProgressDetails] = jsonFormat10(ProgressDetails)

    implicit val validatedFieldFormat: RJF[FieldErrorInfo] = jsonFormat2(FieldErrorInfo)

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
              val fileDataF = fileManagerRegion ? SendToEntity(fileId, SetFormDetails(fileId, vform))
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

                    val fileDataF = fileManagerRegion ? SendToEntity(fileId, GetFileData)
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
          validateForm(form).apply { case ConvertFileForm(fileId, format) =>
            // TODO: filename is still hardcoded here
            val outputDetails = OutputDetails("converted", format)

            val conversionF = fileManagerRegion ? SendToEntity(fileId, RequestForConversion(outputDetails))

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
          case FileIdForm(fileId) =>
            val conversionStatusF = fileManagerRegion ? SendToEntity(fileId, GetConversionStatus)

            onSuccess(conversionStatusF) {
              case progress: ProgressDetails => complete(progress)
              case _ => complete(internalError("Could not retrieve conversion status."))
            }
        }
      }
    }
  }

  def playV1 = pathPrefix("play") {
    path(Remaining) { fileId =>
      get {
        val form = FileIdForm(fileId)
        validateForm(form).apply {
          case FileIdForm(fileId) =>
            val bytesF = fileManagerRegion ? SendToEntity(fileId, Play)

            onComplete(bytesF) {
              case Success(bytes: ByteString) => complete(bytes)
              case _ => complete(internalError("Could not play the file"))
            }
        }
      }
    }
  }


  //  implicit val systemTyped = Adapter.toTyped(system)
  //  implicit val materializerTyped = akka.stream.typed.scaladsl.ActorMaterializer()(systemTyped)
  //  implicit val executionContextTyped: ExecutionContextExecutor = systemTyped.executionContext
  //  implicit val schedulerTyped = systemTyped.scheduler
  val fileListActorSharding = ActorSharding(FileListActor, 3)
  val fileListActorEntityRef = fileListActorSharding.entityRefFor(FileListActor.entityTypeKey, ActorSharding.generateEntityId)

  def uploadV2 = pathPrefix("upload") {
    pathEndOrSingleSlash {
      post {
        entity(as[UploadFileForm]) { form =>
          validateForm(form).apply {
            vform =>
              val fileId = ActorSharding.generateEntityId
              val future: Future[AddFile] = fileListActorEntityRef.ask(ref => AddFormCommand(fileId, vform, ref))
              onSuccess(future) {
                case AddFile(id) =>
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
                    case (fileInfo, fileStream) =>
                      val filePath = Paths.get("/tmp") resolve vform.fileId
                      val sink = FileIO.toPath(filePath)
                      val writeResult = fileStream.runWith(sink)
                      onSuccess(writeResult) { result =>
                        result.status match {
                          case Success(_) => {
                            //complete(s"Successfully written ${result.count} bytes")
                            val path = filePath.toFile.getAbsolutePath
                            val future: Future[GetFile] = fileListActorEntityRef.ask(ref => AddFileCommand(fileId, path, fileInfo.fileName, ref))
                            onSuccess(future) {
                              case GetFile(_, UploadedFile(fileId, _, details, streams, outputFormats)) =>
                                complete(UploadResult(fileId, details.filename, details.description, streams, outputFormats))
                              case _ => complete(internalError("Could not retrieve file data."))
                            }
                          }
                          case Failure(e) => throw e
                        }
                      }
                  }
                }
              }
            }
          }
        }
      }
  }

  def convertV2 = pathPrefix("convert") {
    pathEndOrSingleSlash {
      post {
        entity(as[ConvertFileForm]) { form =>
          validateForm(form).apply {
            vform =>
              val uploadFileDescription: Future[HttpResponse] = Http().singleRequest(
                HttpRequest(uri = s"http://localhost:8080/api/v2/file/upload",
                  method = HttpMethods.POST,
                  entity = Await.result(Marshal(UploadFileForm("67890")).to[RequestEntity], 3.seconds)))

              onSuccess(uploadFileDescription) {
                case HttpResponse(StatusCodes.OK, _, entity, _) => {
                  val nform = Await.result(Unmarshal(entity).to[FileIdForm], 3.seconds)
                  val inputPath = Paths.get(s"http://localhost:8080/api/v2/file/play/${vform.fileId}")

                  val tmpPath = Paths.get(s"/tmp/${vform.fileId}-conversionTo-${nform.fileId}")
                  FFmpeg.atPath(FFmpegConf.Bin)
                    .addInput(UrlInput.fromPath(inputPath))
                    .addOutput(UrlOutput.toPath(tmpPath)
                      .setFormat(vform.format))
                    .execute()

                  val formData = Multipart.FormData(
                    Source(
                      Multipart.FormData.BodyPart.fromPath("file", ContentType(MediaTypes.`application/octet-stream`), tmpPath, 100) :: Nil
                    )
                  )
                  val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri=s"http://localhost:8080/api/v2/file/upload/${nform.fileId}", method = HttpMethods.PUT, entity = formData.toEntity))

                  onSuccess(responseFuture) {
                    case response @ HttpResponse(StatusCodes.OK, _, _, _) =>
                      Files.delete(tmpPath)
                      complete(response)
                  }
                }
              }
          }
        }
      }
    } ~
      path("status" / Remaining) { fileId =>
        get {
          val form = FileIdForm(fileId)
          validateForm(form).apply {
            case FileIdForm(fileId) =>
              val conversionStatusF = fileManagerRegion ? SendToEntity(fileId, GetConversionStatus)

              onSuccess(conversionStatusF) {
                case progress: ProgressDetails => complete(progress)
                case _ => complete(internalError("Could not retrieve conversion status."))
              }
          }
        }
      }
  }

  def playV2 = pathPrefix("play") {
    path(Remaining) { fileId =>
      get {
        val form = FileIdForm(fileId)
        validateForm(form).apply {
          vform =>
            val future: Future[GetFile] = fileListActorEntityRef.ask(ref => GetFileCommand(vform.fileId, ref))
            onSuccess(future) {
              case GetFile(_, UploadedFile(_, path, _, _, _)) =>
                val entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, FileIO.fromPath(Paths.get(path), 100))
                complete(HttpResponse(entity = entity))
              case _ => complete(internalError("Could not play file."))
            }
        }
      }
    }
  }

  def internalError(msg: String) = HttpResponse(InternalServerError, entity = msg)

  def restart(): Unit = {
    val route = pathPrefix("api" / "v1" / "file") { uploadV1 ~ convertV1 ~ playV1} ~
      pathPrefix("api" / "v2" / "file") { uploadV2 ~ convertV2 ~ playV2}
    val port = 8080
    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
