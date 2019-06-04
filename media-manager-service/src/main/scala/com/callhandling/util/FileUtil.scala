package com.callhandling.util

import java.io.File
import java.nio.file.{Files, Path}

import akka.util.ByteString
import com.callhandling.media.FFmpegConf

object FileUtil {
  def writeToTempAndGetPath(data: Array[Byte]) = {
    val uuid = java.util.UUID.randomUUID().toString
    val path = new File(s"${FFmpegConf.StorageDir}/$uuid").toPath

    Files.write(path, data)

    path
  }

  def writeToTempAndGetPath(data: ByteString): Path = writeToTempAndGetPath(data.toArray)
}
