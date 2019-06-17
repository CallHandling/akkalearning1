package com.callhandling.media.io

import akka.stream.IOResult
import com.callhandling.media.OutputFormat

import scala.concurrent.Future

package object instances {
  implicit val fileReader: InputReader[FileStreamIO, Future[IOResult]] =
    new InputReader[FileStreamIO, Future[IOResult]] {
      override def read(input: FileStreamIO, id: String) = input.read(id)

      override def extractStreamDetails(input: FileStreamIO, id: String) =
        input.extractStreamDetails(id)
    }

  implicit val fileWriter: OutputWriter[FileStreamIO, Future[IOResult]] =
    (output: FileStreamIO, id: String, outputFormat: OutputFormat) => output.write(id, outputFormat)
}
