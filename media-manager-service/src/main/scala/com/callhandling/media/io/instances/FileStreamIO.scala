package com.callhandling.media.io.instances

import java.nio.file.{Path, Paths}

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import com.callhandling.media.StreamDetails
import com.callhandling.util.FileUtil._

import scala.concurrent.Future

class FileStreamIO(storagePath: String) {
  import FileStreamIO._

  def read: String => FileByteSource = filePath andThen pathToSource

  def extractStreamDetails: String => List[StreamDetails] =
    filePathString andThen StreamDetails.extractFrom

  def write(id: String, format: String): FileByteSink = {
    val basePath = filePath(id).getParent
    val outputPath = basePath.resolve(s"${id}_$format")
    pathToSink(outputPath)
  }

  def filePath: String => Path = Paths.get(storagePath, _)

  def filePathString: String => String = filePath andThen pathString
}

object FileStreamIO {
  type FileByteSource = Source[ByteString, Future[IOResult]]
  type FileByteSink = Sink[ByteString, Future[IOResult]]

  def pathToSource: Path => FileByteSource = FileIO.fromPath(_)

  def pathToSink: Path => FileByteSink = FileIO.toPath(_)
}
