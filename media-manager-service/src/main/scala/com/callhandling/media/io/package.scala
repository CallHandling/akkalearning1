package com.callhandling.media

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

package object io {
  type BytesInlet[SM] = Source[ByteString, SM]
  type BytesOutlet[SM] = Sink[ByteString, SM]
}
