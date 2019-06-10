package com.callhandling.media.streams

trait InputReader[R] {
  def read(reader: R, id: String): InputStream
}

object InputReader {
  def read[R](reader: R, id: String)(implicit inputReader: InputReader[R]): InputStream =
    inputReader.read(reader, id)
}