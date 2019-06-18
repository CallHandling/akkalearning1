package com.callhandling.media.io

import com.callhandling.media.StreamDetails

trait InputReader[I, M] {
  def read(input: I, id: String): BytesInlet[M]
  def extractStreamDetails(input: I, id: String): List[StreamDetails]
}

object InputReader {
  def read[I, M](input: I, id: String)
      (implicit reader: InputReader[I, M]): BytesInlet[M] =
    reader.read(input, id)

  def extractStreamDetails[I, SM](input: I, id: String)
      (implicit reader: InputReader[I, SM]) =
    reader.extractStreamDetails(input, id)
}