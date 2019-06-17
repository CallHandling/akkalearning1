package com.callhandling.util

import java.io.File
import java.nio.file.{Files, Path, Paths}

import akka.util.ByteString
import com.callhandling.media.FFmpegConf

object FileUtil {
  @deprecated("We may not use this method anymore")
  def writeToTempAndGetPath(data: ByteString): Path = {
    val uuid = java.util.UUID.randomUUID().toString
    val path = new File(s"${FFmpegConf.StoragePath}/$uuid").toPath

    Files.write(path, data.toArray)

    path
  }

  def getOrCreatePath(directory: String, baseDir: String = System.getProperty("java.io.tmpdir")): Path = {
    val path = Paths.get(baseDir, directory)

    // create the file if it does not exist.
    val dir = new File(pathString(path))
    if (!dir.exists) dir.mkdir()

    path
  }

  def pathString: Path => String = _.toAbsolutePath.toString
}
