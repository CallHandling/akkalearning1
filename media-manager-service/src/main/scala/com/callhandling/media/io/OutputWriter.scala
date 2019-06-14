package com.callhandling.media.io

import com.callhandling.media.OutputFormat

trait OutputWriter[O] {
  def write(output: O, id: String, outputFormat: OutputFormat): BytesOutlet
}

object OutputWriter {
  def write[O](output: O, id: String, format: OutputFormat)
      (implicit writer: OutputWriter[O]): BytesOutlet =
    writer.write(output, id, format)
}
