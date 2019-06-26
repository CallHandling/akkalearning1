package com.callhandling.media.io

import akka.stream.IOResult
import com.callhandling.media.OutputFormat

package object instances {
  implicit val fileReader: MediaReader[FileStreamIO, IOResult] =
    new MediaReader[FileStreamIO, IOResult] {
      override def read(input: FileStreamIO, id: String) = Right(input.read(id))

      override def read(input: FileStreamIO, id: String, format: String) =
        input.read(id, format)

      override def mediaStreams(input: FileStreamIO, id: String) =
        input.mediaStreams(id)

      override def outputFormats(input: FileStreamIO, id: String) =
        input.outputFormats(id)
    }

  implicit val fileWriter: MediaWriter[FileStreamIO, IOResult] =
    new MediaWriter[FileStreamIO, IOResult] {
      override def write(output: FileStreamIO, id: String, outputFormat: OutputFormat) =
        Right(output.write(id, Some(outputFormat)))

      override def write(output: FileStreamIO, id: String) = Right(output.write(id, None))
    }
}
