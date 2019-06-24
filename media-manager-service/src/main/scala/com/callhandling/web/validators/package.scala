package com.callhandling.web

import Forms.{ConversionStatusForm, ConvertFileForm, FileIdForm, FormatForm, PlayForm, UploadFileForm}
import com.callhandling.web.validators.Validator._
import com.callhandling.web.validators.ValidationUtils._

// TODO: Redundant validations on multiple validators (code duplications)
package object validators {
  implicit val uploadFileFormValidator: Validator[UploadFileForm] = model =>
    validation(minValidation(5), "description", model.description).toVector

  implicit val convertFileFormValidator: Validator[ConvertFileForm] = {
    case ConvertFileForm(fileId, format, channels, sampleRate, codec) => withRequiredId(fileId,
      validation(requiredValidation, "format", format),
      validation(requiredValidation, "channels", channels),
      validation(requiredValidation, "sampleRate", sampleRate),
      validation(requiredValidation, "codec", codec))
  }

  implicit val fileIdFormValidator: Validator[FileIdForm] = model => withRequiredId(model.fileId)

  // TODO: check against the supported output formats for the file
  implicit val conversionStatusFormValidator: Validator[ConversionStatusForm] = {
    case ConversionStatusForm(fileId, format) => Vector(
      validation(requiredValidation, "fileId", fileId),
      validation(requiredValidation, "format", format)).flatten
  }

  implicit val playFormValiator: Validator[PlayForm] = { case PlayForm(fileId, format) =>
    Vector(validation(requiredValidation, "fileId", fileId)).flatten
  }
}
