package com.callhandling.media.io

import com.callhandling.media.OutputFormat

trait OutputWriter[O, A] {
  def write(output: O, id: String, outputFormat: OutputFormat): OutputStream[A]
}

object OutputWriter {
  def write[O, A](output: O, id: String, format: OutputFormat)
      (implicit writer: OutputWriter[O, A]): OutputStream[A] =
    writer.write(output, id, format)
}
