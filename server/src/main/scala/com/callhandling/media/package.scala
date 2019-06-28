package com.callhandling

package object media {
  type MediaID = String
  type OutputFormat = String

  final case class Rational(numerator: Long, denominator: Long)
}
