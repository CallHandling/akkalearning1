package com.callhandling.media

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future

package object io {
  type BytesInlet[M] = Source[ByteString, Future[M]]
  type BytesOutlet[M] = Sink[ByteString, Future[M]]
}
