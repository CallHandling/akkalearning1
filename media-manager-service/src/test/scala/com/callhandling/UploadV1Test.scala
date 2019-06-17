package com.callhandling

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

import com.callhandling.FileExec._

class UploadV1Test extends Simulation {

  val httpConf = http
    .baseUrl("http://localhost:8080/api/v1")

  val upload = scenario("Upload").exec(Upload.form, Upload.file,
      Convert.file("mp3", 1, 11025, "libmp3lame"),
      Convert.file("wav", 1, 8000, "pcm_s16le"),
      Convert.file("wav", 1, 11500, "pcm_s16le"),
      Convert.file("wav", 1, 22000, "pcm_s16le"),
      Convert.file("wav", 1, 44100, "pcm_s16le"),
      Convert.file("wav", 1, 48000, "pcm_s16le")
    )

  setUp(
    upload.inject(rampUsers(1) during (5 seconds))
  ).protocols(httpConf)

}
