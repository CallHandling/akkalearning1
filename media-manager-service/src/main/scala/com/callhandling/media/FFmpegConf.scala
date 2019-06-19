package com.callhandling.media

import java.io.File
import java.nio.file.Paths

import com.callhandling.util.FileUtil

object FFmpegConf {
  lazy val Bin = Paths.get("/usr/bin/")

  lazy val StoragePath: String =
    FileUtil.pathString(FileUtil.getOrCreatePath("akkalearning"))
}
