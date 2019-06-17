package com.callhandling

import com.callhandling.Forms.{ConvertFileForm, UploadFileForm}
import com.callhandling.typed.util.JsonUtil
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

object FileExec {

  object Upload {
    val form = exec(http("UploadForm")
      .post("/file/upload")
      .body(StringBody(JsonUtil.toJson(UploadFileForm("description")))).asJson
      .check(status is 200)
      .check(jsonPath("$.fileId").saveAs("fileId"))
    )
    val file = exec(http("UploadFile")
      .put("/file/upload/${fileId}")
      .formUpload("file", "request/4minuteAudio.mp3")
      .check(status is 200)
    )
  }

  object Convert {
    def file(format: String, channels: Int, sampleRate: Int, codec: String) = exec(http("ConvertFile")
      .post("/file/convert")
      .body(StringBody(JsonUtil.toJson(ConvertFileForm("$fileId", format, channels, sampleRate, codec)))).asJson
      .check(status is 200)
    ).pause(3.seconds).exec(http("ConvertStatus")
      .put("/file/convert/status/${fileId}")
      .check(status is 200)
    )
  }

}
