package com.callhandling.web

import com.callhandling.web.Forms._
import com.callhandling.web.validators.Required._
import com.callhandling.web.validators.Validator._

package object validators {
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

  implicit val conversionStatusFormValidator: Validator[ConversionStatusForm] = {
    case ConversionStatusForm(fileId, format) => errorMessages(
      requiredId(fileId),
      validate("format", format))
  }

  // TODO: Format is not required, but if it exists, check it's validity
  //  (e.g. minimum length, against available output formats, etc.).
  implicit val playFormValidator: Validator[PlayForm] = { case PlayForm(fileId, _) =>
    errorMessages(requiredId(fileId))
  }

  def requiredId(value: String) = validate("fileId", value)(Required[String])

  def errorMessages(results: Option[FieldErrorInfo]*) = results.toVector.flatten
}
