package com.callhandling.media.io

import com.callhandling.media.OutputFormat

trait OutputWriter[O] {
  def write(output: O, id: String, outputFormat: OutputFormat): OutputBytes
}

object OutputWriter {
  def write[O](output: O, id: String, format: OutputFormat)
      (implicit writer: OutputWriter[O]): OutputBytes =
    writer.write(output, id, format)
}
