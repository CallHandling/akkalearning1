package com.callhandling.media.io.instances

import java.nio.file.{Path, Paths}

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import com.callhandling.media.{OutputFormat, StreamDetails}

import scala.concurrent.Future

class FileStreamIO {
  import FileStreamIO._

  def read: String => FileByteSource = filePath andThen pathToSource

  def extractStreamDetails: String => List[StreamDetails] =
    filePathString andThen StreamDetails.extractFrom

  def write(id: String, format: String): FileByteSink = {
    val outputPath = Paths.get(pathString(filePath(id)), s".format")
    pathToSink(outputPath)
  }
}

object FileStreamIO {
  type FileByteSource = Source[ByteString, Future[IOResult]]
  type FileByteSink = Sink[ByteString, Future[IOResult]]

  lazy val StoragePath: Path = {
    val tempDir = System.getProperty("java.io.tmpdir")
    Paths.get(tempDir, "media_processors")
  }

  def filePath: String => Path = Paths.get(pathString(StoragePath), _)

  def filePathString: String => String = filePath andThen pathString

  def pathString: Path => String = _.toAbsolutePath.toString

  def pathToSource: Path => FileByteSource = FileIO.fromPath(_)

  def pathToSink: Path => FileByteSink = FileIO.toPath(_)
}
