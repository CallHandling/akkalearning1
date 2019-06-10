package com.callhandling.streams

import com.callhandling.media.OutputFormat

trait OutputWriter {
  def write(id: String, outputFormat: OutputFormat): OutputStream
}
