package com.callhandling.media

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

package object io {
  type InputStream[SO, SM] = Source[SO, SM]
  type OutputStream[SI] = Sink[SI, ActorMaterializer]
}
