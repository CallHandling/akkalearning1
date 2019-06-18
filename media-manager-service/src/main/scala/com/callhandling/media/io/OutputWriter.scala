package com.callhandling.media.io

import com.callhandling.media.OutputFormat

trait OutputWriter[O, M] {
  def write(output: O, id: String, outputFormat: OutputFormat): BytesOutlet[M]
}

object OutputWriter {
  def write[O, M](output: O, id: String, format: OutputFormat)
      (implicit writer: OutputWriter[O, M]): BytesOutlet[M] =
    writer.write(output, id, format)
}
