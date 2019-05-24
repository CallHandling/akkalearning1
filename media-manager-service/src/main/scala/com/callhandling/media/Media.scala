package com.callhandling.media

import java.io.{ByteArrayInputStream, File}
import java.nio.file.{Files, Paths}

import akka.util.ByteString
import com.callhandling.media.DataType.Rational
import com.callhandling.media.MediaInformation._
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
    val homeDir = {
      val homeDir = System.getProperty("user.home")
      s"$homeDir/akkalearning"
    }

    val inputStream = new ByteArrayInputStream(data.toArray)

    val path = new File(s"$homeDir/$uuid").toPath
    Files.write(path, data.toArray)

    val result = FFprobe.atPath(bin)
      .setInput(path)
      .setShowStreams(true)
      .execute()

    if (result.getStreams.size < 1) EmptyMediaInformation
    else {
      val stream = result.getStreams.get(0)

      NonEmptyMediaInformation(index = stream.getIndex,
        profile = Option(stream.getProfile),
        codec = Codec(
          name = Option(stream.getCodecName),
          longName = Option(stream.getCodecLongName),
          timeBase = stream.getCodecTimeBase,
          tag = Option(stream.getCodecTag),
          tagString = Option(stream.getCodecTagString)
        ),
        extraData = Option(stream.getExtradata),
        dimensions = Dimensions(
          width = Option(stream.getWidth),
          height = Option(stream.getHeight)
        ),
        codeDimensions = Dimensions(
          width = Option(stream.getCodedWidth),
          height = Option(stream.getCodedHeight)
        ),
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
        refs = Option(stream.getRefs),
        samples = Samples(
          fmt = Option(stream.getSampleFmt),
          rate = Option(stream.getSampleRate)
        ),
        channel = Channel(
          channels = Option(stream.getChannels),
          layout = Option(stream.getChannelLayout),
        ),
        bits = Bits(
          perSample = Option(stream.getBitsPerSample),
          rate = Option(stream.getBitRate),
          maxRate = Option(stream.getMaxBitRate),
          perRawSample = Option(stream.getBitsPerRawSample)
        ),
        id = Option(stream.getId),
        frameRates = FrameRates(
          r = stream.getRFrameRate,
          avg = stream.getAvgFrameRate
        ),
        time = Time(
          code = Option(stream.getTimecode),
          base = Option(stream.getTimeBase),
          startPts = Option(stream.getStartPts),
          startTime = Option(stream.getStartTime),
          duration = Option(stream.getDuration),
          durationTs = Option(stream.getDurationTs)
        ),
        nb = Nb(
          frames = Option(stream.getNbFrames),
          readFrames = Option(stream.getNbReadFrames),
          readPackets = Option(stream.getNbReadPackets)
        ))
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

  final case class Dimensions(width: Option[Int], height: Option[Int])

  final case class Bits(perSample: Option[Int],
    rate: Option[Int], maxRate: Option[Int], perRawSample: Option[Int])

  final case class Nb(frames: Option[Int],
    readFrames: Option[Int], readPackets: Option[Int])

  final case class Samples(fmt: Option[String], rate: Option[Int])

  final case class FrameRates(r: Option[Rational], avg: Option[Rational])

  final case class Time(code: Option[String],
    base: Option[String],
    startPts: Option[Long],
    startTime: Option[Float],
    durationTs: Option[Long],
    duration: Option[Float])

  final case class Channel(channels: Option[Int], layout: Option[String])
}

sealed trait MediaInformation
case object EmptyMediaInformation extends MediaInformation
final case class NonEmptyMediaInformation(index: Int,
  //tag: String => Option[String],
  codec: Codec,
  profile: Option[String],
  extraData: Option[String],
  dimensions: Dimensions,
  codeDimensions: Dimensions,
  hasBFrames: Option[Int],
  aspectRatio: AspectRatio,
  pixFmt: Option[String],
  level: Option[Int],
  color: Color,
  chromaLocation: Option[String],
  fieldOrder: Option[String],
  refs: Option[Int],
  samples: Samples,
  channel: Channel,
  bits: Bits,
  id: Option[String],
  frameRates: FrameRates,
  time: Time,
  nb: Nb) extends MediaInformation