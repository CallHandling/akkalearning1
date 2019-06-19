package com.callhandling

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

package object media {
  type OutputFormat = String

  final case class Rational(numerator: Long, denominator: Long)
}
