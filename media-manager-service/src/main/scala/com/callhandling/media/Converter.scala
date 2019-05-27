package com.callhandling.media

import java.io.ByteArrayInputStream
import java.net.URLConnection

import org.apache.tika.Tika

object Converter {
  def getOutputFormats(data: Array[Byte]) = {
    val mimeType = mimeTypeOf(data)
    val isAudio = mimeType.startsWith("audio")
    val isVideo = mimeType.startsWith("video")

    if (isAudio) Formats.Audios.all
    else if (isVideo) Formats.Videos.all
    else Nil
  }

  def mimeTypeOf: Array[Byte] => String = new Tika().detect
}
