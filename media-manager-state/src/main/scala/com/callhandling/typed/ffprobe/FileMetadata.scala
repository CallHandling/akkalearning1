package com.callhandling.typed.ffprobe

import scala.sys.process._

object FileMetadata {

  def main(args: Array[String]): Unit = {

    val filePath = "/home/ryzen/IdeaProjects/callhandling/akkalearning1/media-manager-state/src/test/resources/01 A Cat Came Fiddling Out Of A Barn.mp3"
    val cmdSeq = Seq("ffprobe", "-i", filePath, "-v","quiet","-print_format", "json", "-show_format", "-show_streams", "-hide_banner")

//    debug(filePath, cmdSeq)

    val jsonMetadata = cmdSeq.lineStream_!.mkString
    val ffprobeMetadata = JsonUtil.fromJson[FFProbeMetadata](jsonMetadata)
    println(ffprobeMetadata)
    println(ffprobeMetadata.format)
    println(ffprobeMetadata.streams.head)
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
