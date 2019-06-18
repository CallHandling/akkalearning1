package com.callhandling

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

import com.callhandling.FileExec._

class UploadV2Test extends Simulation {

  val httpConf = http
    .baseUrl("http://localhost:9090/api/v2")

  val upload = scenario("Upload").exec(Feeder.mp3, Upload.sequence,
    Convert.sequence("mp3", 1, 11025, "libmp3lame"),
    Convert.sequence("wav", 1, 8000, "pcm_s16le"),
    Convert.sequence("wav", 1, 11500, "pcm_s16le"),
    Convert.sequence("wav", 1, 22000, "pcm_s16le"),
    Convert.sequence("wav", 1, 44100, "pcm_s16le"),
    Convert.sequence("wav", 1, 48000, "pcm_s16le")
  )

  setUp(
    upload.inject(rampUsers(1) during (5 seconds))
  ).protocols(httpConf)

}
