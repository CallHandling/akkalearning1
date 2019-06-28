package com.callhandling.media

import scala.language.higherKinds

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future

package object io {
  // Base type for stream IO results
  type MediaIO[IO[_, _], M] = IO[ByteString, Future[M]]

  // Specific types of IO results
  type BytesInlet[M] = MediaIO[Source, M]
  type BytesOutlet[M] = MediaIO[Sink, M]

  // Base type for IO results with error handling capability
  type IOOr[A] = Either[IOError, A]

  // Specific types of IO results that may fail
  type InletOr[M] = IOOr[BytesInlet[M]]
  type OutletOr[M] = IOOr[BytesOutlet[M]]
}
