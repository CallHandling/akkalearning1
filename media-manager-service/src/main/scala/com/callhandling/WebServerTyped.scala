package com.callhandling

import java.nio.file.{Files, Paths}

import akka.actor.typed.javadsl.Adapter
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{entity, _}
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.util.Timeout
import com.callhandling.Forms._
import com.callhandling.Service.JsonSupport.{validateForm, _}
import com.callhandling.Service.{FileIdResult, UploadResult}
import com.callhandling.media.FFmpegConf
import com.callhandling.typed.cluster.ActorSharding
import com.callhandling.typed.persistence._
import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, UrlInput, UrlOutput}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.StdIn

object WebServerTyped {


  def main(args: Array[String]) {
    val system = ActorSystem(mainBehavior, "media-manager-system-typed", ConfigFactory.load("applicationTyped.conf"))
    system.whenTerminated
  }


  trait Protocol
  private final case class WrappedFileListResponse(response: FileListResponse) extends Protocol

  val mainBehavior: Behavior[Protocol] =
    Behaviors.setup {
      context =>
        implicit val system = context.system
        implicit val systemUntyped =  Adapter.toUntyped(system)
        implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
        implicit val executionContext = system.executionContext
        implicit val timeout: Timeout = 3.seconds

        implicit def formValidationRejectionHandler =
          RejectionHandler.newBuilder()
            .handle { case FormValidationRejection(invalidFields) =>
              complete(invalidFields)
            }
            .result()


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
                            val future: Future[GetFile] = fileListActorEntityRef.ask(ref => AddFileCommand(FilePipeline.AmazonS3, fileId, fileStream, fileInfo.fileName, ref))
                            onSuccess(future) {
                              case GetFile(_, UploadedFile(fileId, details, streams, outputFormats), _) =>
                                complete(UploadResult(fileId, details.filename, details.description, streams, outputFormats))
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

        def convertV2 = pathPrefix("convert") {
          pathEndOrSingleSlash {
            post {
              entity(as[ConvertFileForm]) { form =>
                validateForm(form).apply {
                  vform =>
                    val uploadFileDescription: Future[HttpResponse] = Http().singleRequest(
                      HttpRequest(uri = s"http://localhost:8080/api/v2/file/upload",
                        method = HttpMethods.POST,
                        entity = Await.result(Marshal(UploadFileForm("description")).to[RequestEntity], timeout.duration)))

                    onSuccess(uploadFileDescription) {
                      case HttpResponse(StatusCodes.OK, _, entity, _) => {
                        val nform = Await.result(Unmarshal(entity).to[FileIdForm], timeout.duration)
                        val inputPath = Paths.get(s"http://localhost:8080/api/v2/file/play/${vform.fileId}")

                        val tmpPath = Paths.get(s"/tmp/${vform.fileId}-conversionTo-${nform.fileId}")
                        FFmpeg.atPath(FFmpegConf.Bin)
                          .addInput(UrlInput.fromPath(inputPath))
                          .addOutput(UrlOutput.toPath(tmpPath)
                            .addArguments("-ar", vform.sampleRate.toString)
                            .addArguments("-ac", vform.channels.toString)
                            .addArguments("-c:a", vform.codec)
                            .addArguments("-f", vform.format)
                          )
                          .execute()

                        val formData = Multipart.FormData(
                          Source(
                            Multipart.FormData.BodyPart.fromPath("file", ContentType(MediaTypes.`application/octet-stream`), tmpPath) :: Nil
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
//          pathEndOrSingleSlash {
//            post {
//              entity(as[ConvertFileForm]) { form =>
//                validateForm(form).apply {
//                  vform =>
//                    val future: Future[AddFile] = fileListActorEntityRef.ask(ref => ConvertFileCommand(FilePipeline.FileHD, vform, ref))
//                    onSuccess(future) {
//                      case AddFile(id) =>
//                        complete(FileIdResult(id))
//                      case _ => complete(internalError("Could not retrieve file data."))
//                    }
//                }
//              }
//            }
//          } ~
            path("status" / Remaining) { fileId =>
              get {
                val form = FileIdForm(fileId)
                validateForm(form).apply {
                  vform =>
                    complete(internalError("Not implemented"))
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
                  val future: Future[GetFile] = fileListActorEntityRef.ask(ref => GetFileCommand(FilePipeline.FileHD, vform.fileId, ref))
                  onSuccess(future) {
                    case GetFile(_, _, fileSource) =>
                      val entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, fileSource)
                      complete(HttpResponse(entity = entity))
                    case _ => complete(internalError("Could not play file."))
                  }
              }
            }
          }
        }

        val route = //pathPrefix("api" / "v1" / "file") { uploadV1 ~ convertV1 ~ playV1} ~
          pathPrefix("api" / "v2" / "file") { uploadV2 ~ convertV2 ~ playV2}
        val bindingFuture = Http().bindAndHandle(route, "localhost", 9090)
        context.log.info("Server online at http://localhost:9090/\nPress RETURN to stop...")
        StdIn.readLine() // let it run until user presses return
        bindingFuture
          .flatMap(_.unbind()) // trigger unbinding from the port
          .onComplete(_ => system.terminate()) // and shutdown when done

        Behaviors.receiveMessage[Protocol] {
          case wrapped: WrappedFileListResponse => {
            wrapped.response match {
              case AddFile(fileId) =>
                context.log.info("111Added file to FileListActor:")
                context.log.info("111fileId: " + fileId)
                Behaviors.same
              case response@_ =>
                context.log.info("unhandled command"+ response)
                Behaviors.same
            }
          }
        }
        Behaviors.receiveSignal {
          case (_, Terminated(_)) =>
            Behaviors.stopped
        }
    }

  def internalError(msg: String) = HttpResponse(InternalServerError, entity = msg)

}
