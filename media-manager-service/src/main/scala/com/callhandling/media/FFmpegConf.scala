package com.callhandling.media

import java.io.File
import java.nio.file.Paths

object FFmpegConf {
  lazy val Bin = Paths.get("/usr/bin/")

  lazy val StorageDir: String = {
    // get the temporary directory
    val tmpDir = System.getProperty("java.io.tmpdir")

    val storageDir = s"$tmpDir/akkalearning"

    // create the file if it does not exist.
    val dir = new File(storageDir)
    if (!dir.exists) dir.mkdir()

    storageDir
  }
}
