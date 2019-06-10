package com.callhandling.media.streams

import com.callhandling.media.OutputFormat

trait OutputWriter[W] {
  def write(writer: W, id: String, outputFormat: OutputFormat): OutputStream
}

object OutputWriter {
  def write[W](writer: W, id: String, outputFormat: OutputFormat)
      (implicit outputWriter: OutputWriter[W]): OutputStream =
    outputWriter.write(writer, id, outputFormat)
}
