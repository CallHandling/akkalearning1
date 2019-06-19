package com.callhandling.media.io

import com.callhandling.media.OutputFormat

trait MediaWriter[O, M] {
  def write(output: O, id: String, outputFormat: OutputFormat): BytesOutlet[M]
  def write(output: O, id: String): BytesOutlet[M]
  def map()
}

object MediaWriter {
  def write[O, M](output: O, id: String, format: OutputFormat)
      (implicit writer: MediaWriter[O, M]): BytesOutlet[M] =
    writer.write(output, id, format)

  def write[O, M](output: O, id: String)
      (implicit writer: MediaWriter[O, M]): BytesOutlet[M] =
    writer.write(output, id)

  def wasSuccessful[O, M](value: M)(implicit writer: MediaWriter[O, M]) =
    writer.wasSuccessful(value)
}
