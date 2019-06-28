package com.callhandling.media.io

import com.callhandling.media.OutputFormat

trait MediaWriter[O, M] {
  def write(output: O, id: String, outputFormat: OutputFormat): OutletOr[M]
  def write(output: O, id: String): OutletOr[M]
}

object MediaWriter {
  def apply[W, M](implicit writer: MediaWriter[W, M]): MediaWriter[W, M] = writer
}
