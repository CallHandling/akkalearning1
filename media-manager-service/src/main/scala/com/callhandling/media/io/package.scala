package com.callhandling.media

import scala.language.higherKinds

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future

package object io {
  type MediaIO[IO[_, _], M] = IO[ByteString, Future[M]]

  type BytesInlet[M] = MediaIO[Source, M]
  type BytesOutlet[M] = MediaIO[Sink, M]

  type IOOr[B] = Either[IOError, B]
  type InletOr[M] = IOOr[BytesInlet[M]]
  type OutletOr[M] = IOOr[BytesOutlet[M]]
}
