package com.callhandling.media

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

package object io {
  type BytesInlet[M] = Source[ByteString, M]
  type BytesOutlet[M] = Sink[ByteString, M]
}
