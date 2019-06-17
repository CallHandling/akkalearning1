package com.callhandling.media.io

import akka.stream.IOResult

import scala.concurrent.Future

package object instances {
  implicit val fileInputReader: InputReader[FileReader, Future[IOResult]] =
    new InputReader[FileReader, Future[IOResult]] {
      override def read(input: FileReader, id: String) = input.read(id)

      override def extractStreamDetails(input: FileReader, id: String) =
        input.extractStreamDetails(id)
    }
}
