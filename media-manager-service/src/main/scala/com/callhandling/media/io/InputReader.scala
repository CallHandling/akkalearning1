package com.callhandling.media.io

import com.callhandling.media.StreamDetails

trait InputReader[I, SM] {
  def read(input: I, id: String): BytesInlet[SM]
  def extractStreamDetails(input: I, id: String): List[StreamDetails]
}

object InputReader {
  def read[I, SM](input: I, id: String)
      (implicit reader: InputReader[I, SM]): BytesInlet[SM] =
    reader.read(input, id)

  def extractStreamDetails[I, SM](input: I, id: String)
      (implicit reader: InputReader[I, SM]) =
    reader.extractStreamDetails(input, id)
}