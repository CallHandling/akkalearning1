package com.callhandling.media.io

import akka.stream.IOResult
import com.callhandling.media.OutputFormat

import scala.concurrent.Future

package object instances {
  implicit val fileReader: MediaReader[FileStreamIO, Future[IOResult]] =
    new MediaReader[FileStreamIO, Future[IOResult]] {
      override def read(input: FileStreamIO, id: String) = input.read(id)

      override def mediaStreams(input: FileStreamIO, id: String) =
        input.mediaStreams(id)

      override def outputFormats(input: FileStreamIO, id: String) =
        input.outputFormats(id)
    }

  implicit val fileWriter: MediaWriter[FileStreamIO, Future[IOResult]] =
    new MediaWriter[FileStreamIO, Future[IOResult]] {
      override def write(output: FileStreamIO, id: String, outputFormat: OutputFormat) =
        output.write(id, Some(outputFormat))

      override def write(output: FileStreamIO, id: String) = output.write(id, None)
    }
}
