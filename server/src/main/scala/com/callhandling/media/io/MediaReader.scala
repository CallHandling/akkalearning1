package com.callhandling.media.io

import com.callhandling.media.{MediaStream, OutputFormat}
import com.callhandling.media.converters.Formats.Format

trait MediaReader[I, M] {
  def read(input: I, id: String): InletOr[M]
  def read(input: I, id: String, format: OutputFormat): InletOr[M]
  def mediaStreams(input: I, id: String): Vector[MediaStream]
  def outputFormats(input: I, id: String): Vector[Format]
}

object MediaReader {
  def apply[I, M](implicit reader: MediaReader[I, M]): MediaReader[I, M] = reader
}