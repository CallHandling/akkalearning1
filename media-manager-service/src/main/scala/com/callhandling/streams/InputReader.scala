package com.callhandling.streams

trait InputReader[R] {
  def read(id: String, reader: R): InputStream
}

object InputReader {
  def read[R](id: String, reader: R)(implicit inputReader: InputReader[R]): InputStream =
    inputReader.read(id, reader)
}
