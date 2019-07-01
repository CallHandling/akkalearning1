package com.callhandling.media.io

import com.callhandling.media.OutputFormat

trait MediaWriter[O] {
  type Mat

  def write(output: O, id: String, outputFormat: OutputFormat): OutletOr[Mat]
  def write(output: O, id: String): OutletOr[Mat]
}

object MediaWriter {
  def apply[W](implicit writer: MediaWriter[W]): MediaWriter[W] = writer
}
