package com.callhandling.media.io

import com.callhandling.media.MediaStream
import com.callhandling.media.converters.Formats.Format

trait MediaReader[I, M] {
  def read(input: I, id: String): BytesInlet[M]
  def mediaStreams(input: I, id: String): Vector[MediaStream]
  def outputFormats(input: I, id: String): Vector[Format]
}

object MediaReader {
  def read[I, M](input: I, id: String)(implicit reader: MediaReader[I, M]) =
    reader.read(input, id)

  def mediaStreams[I, M](input: I, id: String)(implicit reader: MediaReader[I, M]) =
    reader.mediaStreams(input, id)

  def outputFormats[I, M](input: I, id: String)(implicit reader: MediaReader[I, M]) =
    reader.outputFormats(input, id)
}