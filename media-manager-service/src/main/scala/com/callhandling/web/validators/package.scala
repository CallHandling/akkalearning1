package com.callhandling.web

import com.callhandling.web.Forms._
import com.callhandling.web.validators.Required._
import com.callhandling.web.validators.Validator._

package object validators {
  type Rule[A] = A => Boolean

  implicit val uploadFileFormValidator: Validator[UploadFileForm] = model =>
    validate(minValidation(5), "description", model.description).toVector

  implicit val convertFileFormValidator: Validator[ConvertFileForm] = {
    case ConvertFileForm(fileId, format, channels, sampleRate, codec) =>
      errorMessages(
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
