package com.callhandling.media.io.instances

import java.io.File
import java.nio.file.{Path, Paths}

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import com.callhandling.media.{OutputFormat, StreamDetails}
import com.callhandling.util.FileUtil._

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

  lazy val StoragePath: Path = getOrCreatePath("media_processor")

  def filePath: String => Path = Paths.get(pathString(StoragePath), _)

  def filePathString: String => String = filePath andThen pathString

  def pathToSource: Path => FileByteSource = FileIO.fromPath(_)

  def pathToSink: Path => FileByteSink = FileIO.toPath(_)
}
