package com.callhandling.util

import java.io.File
import java.nio.file.{Files, Path}

import akka.util.ByteString
import com.callhandling.media.FFmpegConf

object FileUtil {
  @deprecated("We may not use this method anymore")
  def writeToTempAndGetPath(data: ByteString): Path = {
    val uuid = java.util.UUID.randomUUID().toString
    val path = new File(s"${FFmpegConf.StorageDir}/$uuid").toPath

    Files.write(path, data.toArray)

    path
  }
}
