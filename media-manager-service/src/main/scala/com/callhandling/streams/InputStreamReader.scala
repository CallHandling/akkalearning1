package com.callhandling.streams

trait InputStreamReader[R] {
  def read(reader: R, id: String): InputStream
}

object InputStreamReader {
  def read[R](reader: R, id: String)(implicit inputReader: InputStreamReader[R]): InputStream =
    inputReader.read(reader, id)
}