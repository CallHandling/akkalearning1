package com.callhandling.media

import java.io.ByteArrayInputStream
import java.net.URLConnection

object Converter {
  def getOutputFormats(data: Array[Byte]) = {
    val mimeType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(data))
    val isAudio = mimeType.startsWith("audio")
    val isVideo = mimeType.startsWith("video")

    if (isAudio) Formats.Audios.all
    else if (isVideo) Formats.Videos.all
    else Nil
  }
}
