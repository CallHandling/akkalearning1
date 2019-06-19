package com.callhandling.typed.http

import java.nio.file.Paths

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.scaladsl.FileIO
import akka.stream.typed.scaladsl.{ActorMaterializer, ActorSink}
import akka.util.Timeout
import com.callhandling.typed.cluster.ActorSharding
import com.callhandling.typed.ffprobe.JsonUtil
import com.callhandling.typed.persistence.{AddFile, FileActor, FileActorSink, FileListActor, FileListResponse, GetFileListCommand, ReturnFile}

import scala.io.StdIn
import scala.util.{Failure, Success}
import scala.concurrent.duration._


object FileUploadApp {

  def main(args: Array[String]) {
    val system = ActorSystem(mainBehavior, "AkkaHttp")
    system.whenTerminated
  }

  trait Protocol
  private final case class WrappedFileListResponse(response: FileListResponse) extends Protocol

  val mainBehavior: Behavior[Protocol] =
    Behaviors.setup {
      context =>
        implicit val system = context.system
        implicit val systemUntyped = akka.actor.ActorSystem("ActorUntyped") //Adapter.toUntyped(system)
        implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
        implicit val executionContext = system.executionContext
        implicit val timeout: Timeout = 3.seconds

        val fileActorSharding = ActorSharding(FileActor, 3)
        val fileListActorSharding = ActorSharding(FileListActor, 3)
        val fileListActorEntityRef = fileListActorSharding.entityRefFor(FileListActor.entityTypeKey, ActorSharding.generateEntityId)

        val replyToFileListActor: ActorRef[FileListResponse] = context.messageAdapter(response => WrappedFileListResponse(response))

        val route = //uploadFileTest(systemUntyped)
          withoutSizeLimit {
            path("fileUpload") {
              post {
                fileUpload("file") {
                  case (fileInfo, fileStream) =>

                    val fileId = ActorSharding.generateEntityId;
                    val fileActorEntityRef = fileActorSharding.entityRefFor(FileActor.entityTypeKey, fileId)
                    val fileActorSinkRef = context.spawn(FileActorSink(fileActorEntityRef, fileListActorEntityRef).main, ActorSharding.generateEntityId)

                    def fileSink = ActorSink.actorRefWithAck(
                      ref = fileActorSinkRef,
                      onCompleteMessage = FileActorSink.Complete,
                      onFailureMessage = FileActorSink.Fail.apply,
                      messageAdapter = FileActorSink.Message.apply,
                      onInitMessage = FileActorSink.Init.apply,
                      ackMessage = FileActorSink.Ack)

                    fileStream.runWith(fileSink)

                    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http2</h1>"))
                }
              }
            }
          }

        val bindingFuture = Http().bindAndHandle(route, "localhost", 8000)
        context.log.info("Server online at http://localhost:8000/\nPress RETURN to stop...")
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
              case ReturnFile(fileId, uploadedFile) =>
                context.log.info("111Retrieve file from FileListActor:")
                context.log.info("111fileId: " + fileId)
                context.log.info("111uploadedFile mediaInfo: "+ uploadedFile.map(a => JsonUtil.toJson(a.multimediaFileInfo)))
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

  def uploadFileTest(systemUntyped: akka.actor.ActorSystem) = {
    path("fileUpload") {
      post {
        fileUpload("file") {
          case (fileInfo, fileStream) =>

            implicit val system = systemUntyped
            implicit val materializer: akka.stream.ActorMaterializer = akka.stream.ActorMaterializer()

            val sink = FileIO.toPath(Paths.get("/tmp") resolve fileInfo.fileName)
            val writeResult = fileStream.runWith(sink)
            onSuccess(writeResult) { result =>
              result.status match {
                case Success(_) => complete(s"Successfully written ${result.count} bytes")
                case Failure(e) => throw e
              }
            }

            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http2</h1>"))
        }
      }
    }
  }

}
