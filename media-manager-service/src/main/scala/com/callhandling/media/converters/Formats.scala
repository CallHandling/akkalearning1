package com.callhandling.media.converters

import java.io.InputStream
import java.nio.file.Path

import com.callhandling.media.converters.Formats.mimeTypeFromBytes
import org.apache.tika.Tika

object Formats {
  final case class Format(code: String, description: String="")

  object Videos {
    lazy val _3gp = Format("3gp")
    lazy val _3g2 = Format("3g2")
    lazy val AVI = Format("avi", "Audio Video Interleave")
    lazy val FLV = Format("flv", "Flash Video")
    lazy val MKV = Format("mkv")
    lazy val MOV = Format("mov")
    lazy val MP4 = Format("mp4")
    lazy val MPEG = Format("mpeg")
    lazy val OGV = Format("ogv")
    lazy val WebM = Format("webm")
    lazy val WMV = Format("wmv")

    lazy val allVideoFormats = _3gp +: _3g2 +: AVI +: FLV +: MKV +: MOV +:
        MP4 +: MPEG +: OGV +: WebM +: WMV +: Vector()
    lazy val all = allVideoFormats ++ Audios.all
  }

  object Audios {
    lazy val AAC = Format("aac")
    lazy val AIFF = Format("aiff")
    lazy val FLAC = Format("flac")
    lazy val M4A = Format("m4a")
    lazy val MMF = Format("mmf")
    lazy val MP3 = Format("mp3")
    lazy val OGG = Format("ogg")
    lazy val OPUS = Format("opus")
    lazy val OGV = Format("ogv")
    lazy val WAV = Format("wav")
    lazy val WMA = Format("wma")

    lazy val all = AAC +: AIFF +: FLAC +: M4A +: MMF +: MP3 +:
      OGG +: OPUS +: OGV +: WAV +: WMA +: Vector()
  }

  def isAudio: MimeDetector = _.startsWith("audio")

  def isVideo: MimeDetector = _.startsWith("video")

  def isSupportedMimeType: MimeDetector = mime => Formats.isAudio(mime) || Formats.isVideo(mime)

  def outputFormatsOf(mimeType: String): Vector[Format] = {
    if (Formats.isAudio(mimeType)) Formats.Audios.all
    else if (Formats.isVideo(mimeType)) Formats.Videos.all
    else Vector.empty
  }

  def outputFormatsOf(path: Path): Vector[Format] = {
    val mimeType = new Tika().detect(path)
    Formats.outputFormatsOf(mimeType)
  }

  def outputFormatsOf(inputStream: InputStream): Vector[Format] =
    outputFormatsOf(mimeTypeFromStream(inputStream))


  def mimeTypeFromBytes: Array[Byte] => String = new Tika().detect
  def mimeTypeFromStream: InputStream => String = new Tika().detect
}
