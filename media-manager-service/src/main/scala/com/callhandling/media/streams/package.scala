package com.callhandling.media

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

package object streams {
  type InputStream = Source[ByteString, Any]
  type OutputStream = Sink[ByteString, ActorMaterializer]
}
