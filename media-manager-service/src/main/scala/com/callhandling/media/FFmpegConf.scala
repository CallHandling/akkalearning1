package com.callhandling.media

import java.nio.file.Paths

object FFmpegConf {
  lazy val Bin = Paths.get("/usr/bin/")
  lazy val HomeDir = {
    val homeDir = System.getProperty("user.home")
    s"$homeDir/akkalearning"
  }
}
