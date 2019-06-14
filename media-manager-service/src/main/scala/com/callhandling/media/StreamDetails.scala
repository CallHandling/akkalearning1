package com.callhandling.media

import java.nio.file.{Path}

import akka.util.ByteString
import com.callhandling.media.StreamDetails._
import com.callhandling.util.FileUtil
import com.github.kokorin.jaffree.ffprobe.{FFprobe, Stream}
import com.github.kokorin.jaffree.{Rational => JRational}

import scala.collection.JavaConverters._

object StreamDetails {
  type Streams = List[StreamDetails]

  implicit def jRationalToRational(jRational: JRational): Option[Rational] =
    Option(jRational).map(rational =>
      Rational(rational.numerator, rational.denominator))

  def extractFrom(path: String): Streams = {
    val result = FFprobe.atPath(FFmpegConf.Bin)
      .setInput(path)
      .setShowStreams(true)
      .execute()

    asScalaIterator[Stream](result.getStreams.iterator()).map { stream =>
      StreamDetails(index = stream.getIndex,
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
    }.toList
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

final case class StreamDetails(index: Int,
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
  nb: Nb)