package controllers

import java.nio.ByteBuffer

import com.typesafe.config.ConfigFactory
import twirl.Implicits._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext.Implicits.global
import boopickle.Default._
import com.callhandling.web.validators.Validator.FormValidationRejection
import services.ApiService
import v2.spatutorial.shared.Api

object Router extends autowire.Server[ByteBuffer, Pickler, Pickler] {
  override def read[R: Pickler](p: ByteBuffer) = Unpickle[R].fromBytes(p)
  override def write[R: Pickler](r: R) = Pickle.intoBytes(r)
}

object Application {
  val apiService = new ApiService()

  def index =  pathSingleSlash {
    get {
      complete {
        views.html.index.render("SPA tutorial")
      }
    }
  }

  def assetsFonts = pathPrefix("assets" / "fonts" / Remaining) { file =>
    encodeResponse {
      getFromResource("public/lib/fontawesome/fonts/" + file)
    }
  }

  def assetsAny = pathPrefix("assets" / Remaining) { file =>
    encodeResponse {
      getFromResource("public/" + file)
    }
  }

  def autowireApi =  path("api" / Segments) { s =>
    post {
      entity(as[String]) { e =>
        println(s"Request path: ${e.mkString}")

        // get the request body as ByteBuffer
        val b = ByteBuffer.wrap(e.getBytes())

        // call Autowire route
        complete {
          Router.route[Api](apiService)(
            autowire.Core.Request(
              s,
              Unpickle[Map[String, ByteBuffer]].fromBytes(b)
            )
          ).map(buffer => {
            val data = Array.ofDim[Byte](buffer.remaining())
            buffer.get(data)
            data
          })
        }
      }
    }
  }

  def logging = pathPrefix("logging") {
    pathEndOrSingleSlash {
      post {
        entity(as[String]) { e =>
          println(s"CLIENT - $e")
            complete("")
//          complete(HttpEntity(ContentTypes.`application/json`, entity.dataBytes))
        }
      }
    }
  }

  def main(args: Array[String]) {
    implicit val system = ActorSystem("media-manager-system")
    implicit val materializer = ActorMaterializer()

    val config = ConfigFactory.load()
    val interface = config.getString("http.interface")
    val port = config.getInt("http.port")

    val uploadRoute = com.callhandling.WebServer.route(system, materializer)
    import com.callhandling.web.JsonSupport._
    implicit def formValidationRejectionHandler: RejectionHandler =
      RejectionHandler.newBuilder()
        .handle { case FormValidationRejection(invalidFields) =>
          complete(invalidFields)
        }
        .result()

    val route = { index ~ assetsFonts ~ assetsAny ~ logging ~ uploadRoute ~ autowireApi }
    Http().bindAndHandle(route, interface, port)

    println(s"Server online at http://$interface:$port")
  }
}
