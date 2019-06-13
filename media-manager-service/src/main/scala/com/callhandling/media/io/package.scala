package com.callhandling.media

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

package object io {
  type InputBytes[SM] = Source[ByteString, SM]
  type OutputBytes = Sink[ByteString, ActorMaterializer]
}
