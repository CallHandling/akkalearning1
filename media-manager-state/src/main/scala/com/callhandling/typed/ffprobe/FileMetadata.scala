package com.callhandling.typed.ffprobe

import scala.sys.process._

object FileMetadata {

  def get(filePath: String): FFProbeMetadata = {
    val cmdSeq = Seq("ffprobe", "-i", filePath, "-v","quiet","-print_format", "json", "-show_format", "-show_streams", "-hide_banner")
    val jsonMetadata = cmdSeq.lineStream_!.mkString
    JsonUtil.fromJson[FFProbeMetadata](jsonMetadata)
  }

  def getDuration(filePath: String): String = {
    val cmdSeq = Seq("ffmpeg", "-i", filePath, "-f","null","-")
    val jsonMetadata = cmdSeq.lineStream_!.mkString
    jsonMetadata
  }

  def main(args: Array[String]): Unit = {
    val filePath = "/home/ryzen/IdeaProjects/callhandling/akkalearning1/media-manager-state/src/test/resources/Plain Functional Programming by Martin Odersky.mp4"

    //    debug(filePath, cmdSeq)

    val ffprobeMetadata = get(filePath)
    println(ffprobeMetadata)
    println(ffprobeMetadata.format)
    println(ffprobeMetadata.format.format_name)
    println(ffprobeMetadata.streams.head)
    println(ffprobeMetadata.streams.head.duration_ts)

    val duration = getDuration(filePath)
    println(duration)
  }

  def debug(filePath: String, cmdSeq: Seq[String]): Unit = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val s = cmdSeq ! ProcessLogger(stdout append _, stderr append _)
    println(cmdSeq)
    println(s)
    println("stdout: " + stdout)
    println("stderr: " + stderr)
  }

}
