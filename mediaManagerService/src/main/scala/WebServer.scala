import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import java.io.File

object WebServer {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val FileFieldName = "file"

    val route = path("fileUpload") {
      entity(as[Multipart.FormData]) { formData =>
        val futureParts: Future[Map[String, Any]] = formData.parts.mapAsync(1) {
          case part: BodyPart if part.filename.isDefined && part.name == FileFieldName =>

          case part: BodyPart => part.toStrict(2.seconds).map(strict =>
            part.name -> strict.entity.data.utf8String)
        }.runFold(Map.empty[String, Any]) (_ + _)

        onSuccess(futureParts) { parts =>
          complete("ok")
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

  trait MultiFormData
  final case class FileData(dataBytes: Source[ByteString, Any]) extends MultiFormData
  final case class FormFieldData()
}
