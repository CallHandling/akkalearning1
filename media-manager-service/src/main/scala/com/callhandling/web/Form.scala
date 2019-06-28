package com.callhandling.web

object Form {
  final case class UploadFileForm(description: String) extends AnyVal

  final case class ConvertFileForm(
      fileId: String,
      format: String,
      channels: Int,
      sampleRate: Int,
      codec: String)

  final case class FileIdForm(fileId: String) extends AnyVal

  final case class ConversionStatusForm(fileId: String, format: String)

  final case class FormatForm(format: String) extends AnyVal

  final case class OptionalFormatForm(format: Option[String])

  final case class PlayForm(fileId: String, format: Option[String])
}
