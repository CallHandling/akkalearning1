package com.callhandling.media.io

trait InputReader[I, SO, SM] {
  def read(input: I, id: String): InputStream[SO, SM]
}

object InputReader {
  def read[I, SO, SM](input: I, id: String)(implicit reader: InputReader[I, SO, SM]): InputStream[SO, SM] =
    reader.read(input, id)
}