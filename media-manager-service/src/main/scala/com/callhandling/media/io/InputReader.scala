package com.callhandling.media.io

trait InputReader[I, SM] {
  def read(input: I, id: String): InputBytes[SM]
}

object InputReader {
  def read[I, SM](input: I, id: String)
      (implicit reader: InputReader[I, SM]): InputBytes[SM] =
    reader.read(input, id)
}