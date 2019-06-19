package com.callhandling.media.io.instances

import java.nio.file.{Path, Paths}

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import com.callhandling.media.converters.Formats
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.{MediaID, MediaStream, OutputFormat}
import com.callhandling.util.FileUtil._

import scala.concurrent.Future

class FileStreamIO(storagePath: String) {
  import FileStreamIO._

  def read: MediaID => FileByteSource = filePath andThen pathToSource

  def mediaStreams: MediaID => Vector[MediaStream] =
    filePathString andThen MediaStream.extractFrom

  def write(id: MediaID, format: Option[OutputFormat]): FileByteSink = {
    val basePath = filePath(id).getParent
    val suffix = format.map("_" + _).getOrElse("")
    val outputPath = basePath.resolve(s"$id$suffix")
    pathToSink(outputPath)
  }

  def outputFormats: MediaID => Vector[Format] =
    filePath andThen Formats.outputFormatsOf

  def filePath: MediaID => Path = Paths.get(storagePath, _)

  def filePathString: MediaID => String = filePath andThen pathString

  def wasSuccessful(result: FileByteSource) =
}

object FileStreamIO {
  type FileByteSource = Source[ByteString, Future[IOResult]]
  type FileByteSink = Sink[ByteString, Future[IOResult]]

  def pathToSource: Path => FileByteSource = FileIO.fromPath(_)

  def pathToSink: Path => FileByteSink = FileIO.toPath(_)
}
