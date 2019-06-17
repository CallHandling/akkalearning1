package com.callhandling.media.io.instances

import java.nio.file.{Path, Paths}

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.callhandling.media.StreamDetails

import scala.concurrent.Future

class FileReader {
  import FileReader._

  def read: String => FileByteSource = filePath andThen pathToSource

  def pathToSource: Path => FileByteSource = FileIO.fromPath(_)

  def extractStreamDetails: String => List[StreamDetails] =
    pathString andThen StreamDetails.extractFrom
}

object FileReader {
  type FileByteSource = Source[ByteString, Future[IOResult]]

  lazy val StorageDir: String = {
    val tempDir = System.getProperty("java.io.tmpdir")
    s"$tempDir/conversions"
  }

  def filePath: String => Path = Paths.get(s"$StorageDir", _)

  def pathString: String => String = filePath andThen (_.toAbsolutePath.toString)
}
