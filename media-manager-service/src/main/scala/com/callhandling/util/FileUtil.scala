package com.callhandling.util

import java.io.File
import java.nio.file.{Path, Paths}

object FileUtil {
  def createPath(
      directory: String,
      baseDir: String = System.getProperty("java.io.tmpdir")): Path = {
    val path = Paths.get(baseDir, directory)

    // create the file if it does not exist.
    val dir = new File(pathString(path))
    if (!dir.exists) dir.mkdir()

    path
  }

  def pathString: Path => String = _.toAbsolutePath.toString
}
