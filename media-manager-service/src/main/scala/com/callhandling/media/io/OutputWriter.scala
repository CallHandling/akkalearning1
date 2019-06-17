package com.callhandling.media.io

import com.callhandling.media.OutputFormat

trait OutputWriter[O, SM] {
  def write(output: O, id: String, outputFormat: OutputFormat): BytesOutlet[SM]
}

object OutputWriter {
  def write[O, SM](output: O, id: String, format: OutputFormat)
      (implicit writer: OutputWriter[O, SM]): BytesOutlet[SM] =
    writer.write(output, id, format)
}
