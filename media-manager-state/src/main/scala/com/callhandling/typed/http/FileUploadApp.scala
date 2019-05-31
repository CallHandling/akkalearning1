package com.callhandling.typed.http

import java.nio.file.Paths

import akka.NotUsed
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.scaladsl.FileIO
import akka.stream.typed.scaladsl.{ActorMaterializer, ActorSink}
import com.callhandling.typed.cluster.ActorSharding
import com.callhandling.typed.persistence.{FileActor, FileActorSink, FileListActor}

import scala.io.StdIn
import scala.util.{Failure, Success}

object FileUploadApp {

  def main(args: Array[String]) {
    val system = ActorSystem(mainBehavior, "AkkaHttp")
    system.whenTerminated
  }

  val mainBehavior: Behavior[NotUsed] =
    Behaviors.setup {
      context =>
        implicit val system = context.system
        implicit val systemUntyped = akka.actor.ActorSystem("ActorUntyped") //Adapter.toUntyped(system)
        implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
        implicit val executionContext = system.executionContext

        val fileActorSharding = ActorSharding(FileActor, 3)
        val fileListActorSharding = ActorSharding(FileListActor, 3)

        val route = //uploadFileTest(systemUntyped)
          withoutSizeLimit {
            path("fileUpload") {
              post {
                fileUpload("file") {
                  case (fileInfo, fileStream) =>

                    val fileActorEntityRef = fileActorSharding.entityRefFor(FileActor.entityTypeKey, ActorSharding.generateEntityId)
                    val fileListActorEntityRef = fileListActorSharding.entityRefFor(FileListActor.entityTypeKey, ActorSharding.generateEntityId)
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
        context.log.info("Server online at http://localhost:8080/\nPress RETURN to stop...")
        StdIn.readLine() // let it run until user presses return
        bindingFuture
          .flatMap(_.unbind()) // trigger unbinding from the port
          .onComplete(_ => system.terminate()) // and shutdown when done

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
