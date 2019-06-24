package com.callhandling.media.io.instances

import java.nio.file.{Path, Paths}

import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import com.callhandling.media.converters.Formats
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.io.{BytesInlet, BytesOutlet}
import com.callhandling.media.{MediaID, MediaStream, OutputFormat}
import com.callhandling.util.FileUtil._

class FileStreamIO(storagePath: String) {
  import FileStreamIO._

  def read: MediaID => FileByteSource = filePath andThen pathToSource
  def read(id: MediaID, format: OutputFormat): FileByteSource =
    pathToSource(formatPath(id, Some(format)))

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

    // TODO: Perhaps we need to improve this one, making the path unique so
    //  as to avoid multiple workers writing to the same file
    basePath.resolve(s"$id$suffix")
  }
}

object FileStreamIO {
  type FileByteSource = BytesInlet[IOResult]
  type FileByteSink = BytesOutlet[IOResult]

  def pathToSource: Path => FileByteSource = FileIO.fromPath(_)

  def pathToSink: Path => FileByteSink = FileIO.toPath(_)
}
