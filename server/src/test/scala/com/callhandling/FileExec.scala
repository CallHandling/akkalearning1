package com.callhandling

import com.callhandling.web.Forms.{ConvertFileForm, UploadFileForm}
import com.callhandling.typed.util.JsonUtil
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

object FileExec {

  object Feeder {
    val mp3 = exec(http("Home").get("/"))
      .feed(csv("request/mp3.csv").random)
  }

  object Upload {
    val form = exec(http("UploadForm")
      .post("/file/upload")
      .body(StringBody(JsonUtil.toJson(UploadFileForm("${description}")))).asJson
      .check(status is 200)
      .check(jsonPath("$.fileId").saveAs("fileId"))
    )
    val file = doIf("${fileId.exists()}") {
      exec(http("UploadFile")
        .put("/file/upload/${fileId}")
        //.formUpload("file", "request/${file}")
        .check(status is 200 saveAs("isUploadFile"))
      )
    }

    val sequence = exec(form, file)
  }

  object Convert {
    def file(format: String, channels: Int, sampleRate: Int, codec: String) = doIf("${isUploadFile.exists()}") {
      exec(http("ConvertFile")
        .post("/file/convert")
        .body(StringBody(JsonUtil.toJson(ConvertFileForm("$fileId", format, channels, sampleRate, codec)))).asJson
        .check(status is 200)
        .check(jsonPath("$.fileId").saveAs("fileIdConverted"))
      )
    }
    val status_ = doIf("${fileIdConverted.exists()}") {
      exec(http("ConvertStatus")
        .put("/file/convert/status/${fileIdConverted}")
        .check(status is 200)
      )
    }

    def sequence(format: String, channels: Int, sampleRate: Int, codec: String) = exec(file(format, channels, sampleRate, codec), status_)
  }

}
