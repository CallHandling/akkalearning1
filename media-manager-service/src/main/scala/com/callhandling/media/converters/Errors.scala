package com.callhandling.media.converters

sealed trait ConversionError

case object NoMediaStreamAvailable extends ConversionError
case object StreamInfoIncomplete extends ConversionError
final case class ConversionErrorSet(errors: List[ConversionError]) extends ConversionError
