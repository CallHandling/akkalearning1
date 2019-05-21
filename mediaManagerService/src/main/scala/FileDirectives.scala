import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.{Directive1, MissingFormFieldRejection}
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.http.scaladsl.server.directives._
import BasicDirectives._
import RouteDirectives._
import FutureDirectives._
import MarshallingDirectives._
import akka.Done
import akka.http.scaladsl.model.Multipart.FormData.BodyPart

import scala.concurrent.Future
import scala.concurrent.duration._

object FileDirectives {
  type FileAndFormData = (MultiFormInfo, Source[ByteString, Any])

  /*
  def fileAndFormDataUpload(fileFieldNames: List[String], formFieldNames: List[String]): Directive1[FileAndFormData] =
    entity(as[Multipart.FormData]).flatMap { formData =>
      extractRequestContext.flatMap { ctx =>
        implicit val mat = ctx.materializer
        implicit val ec = ctx.executionContext

        def checkFieldNames(fieldName: String, fieldNames: List[String]) =
          fieldNames.isEmpty || fieldNames.contains(fieldName)

        val multiPartSource = formData.parts.fold[FileAndFormData]((FieldDataInfo(), Source.empty[ByteString])) {
          // If the field is a file, construct a file info and byte source out of it.
          case ((formInfo, _), part) if part.filename.isDefined && checkFieldNames(part.name, fileFieldNames) =>
            val fileInfo = FileInfo(part.name, part.filename.get, part.entity.contentType)
            (FileAndFieldDataInfo(fileInfo, formInfo.fields), part.entity.dataBytes.)

          // Otherwise, update the fields.
          case ((FieldDataInfo(fields), byteSource), part) if checkFieldNames(part.name, formFieldNames) =>
            (FieldDataInfo(fields + part.toStrict(2.seconds).map(strict => (part.name -> strict.entity.data.utf8String))))
        }

        formData.parts.map { part =>
          if (part.filename.isDefined && checkFieldNames(part.name, fileFieldNames))
        }

        val onePartF = onePartSource.runWith(Sink.headOption[(FileInfo, Source[ByteString, Any])])

        onSuccess(onePartF)
      }

    }.flatMap {
      case Some(tuple) => provide(tuple)
      case None        => reject(MissingFormFieldRejection(""))
    }*/

  trait MultiFormInfo {
    def fileInfo: FileInfo
    def fields: Map[String, String]
  }

  final case class FieldDataInfo(fields: Map[String, String] = Map.empty) extends MultiFormInfo {
    override def fileInfo = throw new IllegalArgumentException("Empty form has no file info")
  }

  final case class FileAndFieldDataInfo(fileInfo: FileInfo, fields: Map[String, String]) extends MultiFormInfo
}
