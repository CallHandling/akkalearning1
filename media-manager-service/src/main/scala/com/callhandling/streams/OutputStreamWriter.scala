package com.callhandling.streams

import com.callhandling.media.OutputFormat

trait OutputStreamWriter[W] {
  def write(writer: W, id: String, outputFormat: OutputFormat): OutputStream
}

object OutputStreamWriter {
  def write[W](writer: W, id: String, outputFormat: OutputFormat)
      (implicit outputWriter: OutputStreamWriter[W]): OutputStream =
    outputWriter.write(writer, id, outputFormat)
}
