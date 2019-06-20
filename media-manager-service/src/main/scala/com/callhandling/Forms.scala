package com.callhandling

object Forms {
  final case class UploadFileForm(description: String)
  final case class ConvertFileForm(fileId: String, format: String, channels: Int, sampleRate: Int, codec: String)
  final case class FileIdForm(fileId: String)
}
