package com.callhandling.web

import akka.http.scaladsl.server.Rejection
import com.callhandling.web.Forms._
import com.callhandling.web.validators.Required._
import com.callhandling.web.validators.Validator._

package object validators {
  final case class FieldErrorInfo(name: String, error: String)
  final case class FormValidationRejection(invalidFields: Vector[FieldErrorInfo]) extends Rejection

  implicit val uploadFileFormValidator: Validator[UploadFileForm] = model => errorMessages(
    validate("description", model.description)(Minimum[String]))

  implicit val convertFileFormValidator: Validator[ConvertFileForm] = {
    case ConvertFileForm(fileId, format, channels, sampleRate, codec) => errorMessages(
      requiredId(fileId),
      validate("format", format)(Required[String]),
      validate("channels", channels)(Required[Int]),
      validate("sampleRate", sampleRate)(Required[Int]),
      validate("codec", codec)(Required[String]))
  }

  implicit val fileIdFormValidator: Validator[FileIdForm] = model =>
    errorMessages(requiredId(model.fileId))

  // TODO: check against the supported output formats for the file
  implicit val conversionStatusFormValidator: Validator[ConversionStatusForm] = {
    case ConversionStatusForm(fileId, format) => errorMessages(
      requiredId(fileId),
      validate("format", format))
  }

  implicit val playFormValidator: Validator[PlayForm] = { case PlayForm(fileId, format) =>
    errorMessages(requiredId(fileId))
  }

  def requiredId(value: String) = validate("fileId", value)(Required[String])

  def errorMessages(results: Option[FieldErrorInfo]*) = results.toVector.flatten
}
