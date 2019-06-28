package com.callhandling.media.io.instances

import java.io.File
import java.nio.file.{Path, Paths}

import cats.syntax.either._
import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import com.callhandling.media.converters.Formats
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.io.{BytesInlet, BytesOutlet, IOValidation, InletOr, MediaNotFound}
import com.callhandling.media.{MediaID, MediaStream, OutputFormat}
import com.callhandling.util.FileUtil._

class FileStreamIO(storagePath: String) {
  import FileStreamIO._

  def read: MediaID => FileByteSource = filePath andThen pathToSource

  def read(id: MediaID, format: OutputFormat): InletOr[IOResult] = {
    val path = formatPath(id, Some(format))

    if (new File(pathString(path)).isFile) pathToSource(path).asRight[IOValidation]
    else MediaNotFound.asLeft[FileByteSource]
  }

  def mediaStreams: MediaID => Vector[MediaStream] =
    filePathString andThen MediaStream.extractFrom

  def write(id: MediaID, format: Option[OutputFormat]): FileByteSink =
    pathToSink(formatPath(id, format))

  def outputFormats: MediaID => Vector[Format] =
    filePath andThen Formats.outputFormatsOf

  def filePath: MediaID => Path = Paths.get(storagePath, _)

  def filePathString: MediaID => String = filePath andThen pathString

  def formatPath(id: MediaID, format: Option[OutputFormat]) = {
    val basePath = filePath(id).getParent
    val suffix = format.map("_" + _).getOrElse("")

    basePath.resolve(s"$id$suffix")
  }
}

object FileStreamIO {
  type FileByteSource = BytesInlet[IOResult]
  type FileByteSink = BytesOutlet[IOResult]

  def pathToSource: Path => FileByteSource = FileIO.fromPath(_)

  def pathToSink: Path => FileByteSink = FileIO.toPath(_)
}
