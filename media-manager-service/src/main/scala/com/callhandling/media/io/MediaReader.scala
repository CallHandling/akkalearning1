package com.callhandling.media.io

import com.callhandling.media.{MediaStream, OutputFormat}
import com.callhandling.media.converters.Formats.Format

trait MediaReader[I] {
  type Mat

  def read(input: I, id: String): InletOr[Mat]
  def read(input: I, id: String, format: OutputFormat): InletOr[Mat]
  def mediaStreams(input: I, id: String): Vector[MediaStream]
  def outputFormats(input: I, id: String): Vector[Format]
}

object MediaReader {
  def apply[I](implicit reader: MediaReader[I]): MediaReader[I] = reader
}