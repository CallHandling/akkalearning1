package com.callhandling

import java.io.{ByteArrayInputStream, File}
import java.nio.file.{Files, Paths}

import akka.util.ByteString
import com.callhandling.DataType.Rational
import com.callhandling.MediaInformation.{AspectRatio, Codec, Color}
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import com.github.kokorin.jaffree.ffprobe.FFprobe
import com.github.kokorin.jaffree.{Rational => JRational}

object DataType {
  final case class Rational(numerator: Long, denominator: Long)

  implicit def jRationalToRational(jRational: JRational): Option[Rational] =
    Option(jRational).map(rational =>
      Rational(rational.numerator, rational.denominator))
}

object MediaInformation {
  def extractFrom(uuid: String, data: ByteString) = {
    // TODO: Consider making these two values configurable (as opposed to being hardcoded values)
    val bin = Paths.get("/usr/bin/")
    val tempDir = "/tmp/akkalearning"

    val inputStream = new ByteArrayInputStream(data.toArray)

    val path = new File(s"$tempDir/$uuid").toPath
    Files.write(path, data.toArray)

    val result = FFprobe.atPath(bin)
      .setInput(path)
      .setShowStreams(true)
      .execute()

    if (result.getStreams.size < 1) EmptyMediaInformation
    else {
      val stream = result.getStreams.get(0)
      NonEmptyMediaInformation(index = stream.getIndex,
        tag = { key => Option(stream.getTag(key)) },
        profile = Option(stream.getProfile),
        codec = Codec(
          name = Option(stream.getCodecName),
          longName = Option(stream.getCodecLongName),
          timeBase = stream.getCodecTimeBase,
          tag = Option(stream.getCodecTag),
          tagString = Option(stream.getCodecTagString)
        ),
        extraData = Option(stream.getExtradata),
        width = Option(stream.getWidth),
        height = Option(stream.getHeight),
        codeWidth = Option(stream.getCodedWidth),
        codeHeight = Option(stream.getCodedHeight),
        hasBFrames = Option(stream.hasBFrames),
        aspectRatio = AspectRatio(
          sample = stream.getSampleAspectRatio,
          display = stream.getDisplayAspectRatio
        ),
        pixFmt = Option(stream.getPixFmt),
        level = Option(stream.getLevel),
        color = Color(
          range = Option(stream.getColorRange),
          space = Option(stream.getColorSpace),
          transfer = Option(stream.getColorTransfer),
          primaries = Option(stream.getColorPrimaries)
        ),
        chromaLocation = Option(stream.getChromaLocation),
        fieldOrder = Option(stream.getFieldOrder),
        timecode = Option(stream.getTimecode),
        refs = Option(stream.getRefs),
        sampleFmt = Option(stream.getSampleFmt),
        sampleRate = Option(stream.getSampleRate),
        channels = Option(stream.getChannels),
        channelLayout = Option(stream.getChannelLayout),
        bitsPerSample = Option(stream.getBitsPerSample),
        id = Option(stream.getId),
        rFrameRate = stream.getRFrameRate,
        avgFrameRate = stream.getAvgFrameRate,
        timeBase = Option(stream.getTimeBase),
        startPts = Option(stream.getStartPts),
        startTime = Option(stream.getStartTime),
        duration = Option(stream.getDuration),
        durationTs = Option(stream.getDurationTs),
        bitRate = Option(stream.getBitRate),
        maxBitRate = Option(stream.getMaxBitRate),
        bitsPerRawSample = Option(stream.getBitsPerRawSample),
        nbFrames = Option(stream.getNbFrames),
        nbReadFrames = Option(stream.getNbReadFrames),
        nbReadPackets = Option(stream.getNbReadPackets))
    }
  }

  final case class Codec(name: Option[String],
    longName: Option[String],
    timeBase: Option[Rational],
    tag: Option[String],
    tagString: Option[String],
  )

  final case class AspectRatio(sample: Option[Rational], display: Option[Rational])

  final case class Color(range: Option[String],
    space: Option[String],
    transfer: Option[String],
    primaries: Option[String])
}

sealed trait MediaInformation
case object EmptyMediaInformation extends MediaInformation
final case class NonEmptyMediaInformation(index: Int,
  tag: String => Option[String],
  codec: Codec,
  profile: Option[String],
  extraData: Option[String],
  width: Option[Int],
  height: Option[Int],
  codeWidth: Option[Int],
  codeHeight: Option[Int],
  hasBFrames: Option[Int],
  aspectRatio: AspectRatio,
  pixFmt: Option[String],
  level: Option[Int],
  color: Color,
  chromaLocation: Option[String],
  fieldOrder: Option[String],
  timecode: Option[String],
  refs: Option[Int],
  sampleFmt: Option[String],
  sampleRate: Option[Int],
  channels: Option[Int],
  channelLayout: Option[String],
  bitsPerSample: Option[Int],
  id: Option[String],
  rFrameRate: Option[Rational],
  avgFrameRate: Option[Rational],
  timeBase: Option[String],
  startPts: Option[Long],
  startTime: Option[Float],
  durationTs: Option[Long],
  duration: Option[Float],
  bitRate: Option[Int],
  maxBitRate: Option[Int],
  bitsPerRawSample: Option[Int],
  nbFrames: Option[Int],
  nbReadFrames: Option[Int],
  nbReadPackets: Option[Int]) extends MediaInformation