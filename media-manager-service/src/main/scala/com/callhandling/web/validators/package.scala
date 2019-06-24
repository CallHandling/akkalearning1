package com.callhandling.web

import Forms.{ConversionStatusForm, ConvertFileForm, FileIdForm, FormatForm, UploadFileForm}
import com.callhandling.web.validators.Validator._
import com.callhandling.web.validators.ValidationUtils._

package object validators {
  implicit val uploadFileFormValidator: Validator[UploadFileForm] = model =>
    validation(minValidation(5), "fileId", model.description).toVector

  implicit val convertFileFormValidator: Validator[ConvertFileForm] = {
    case ConvertFileForm(fileId, format, channels, sampleRate, codec) => Vector(
      validation(requiredValidation, "format", format),
      validation(requiredValidation, "fileId", fileId),
      validation(requiredValidation, "channels", channels),
      validation(requiredValidation, "sampleRate", sampleRate),
      validation(requiredValidation, "codec", codec)).flatten
  }

  implicit val fileIdFormValidator: Validator[FileIdForm] = model =>
    validation(ValidationUtils.requiredValidation, "fileId", model.fileId).toVector

  // TODO: check against the supported output formats for the file
  implicit val conversionStatusFormValidator: Validator[ConversionStatusForm] = {
    case ConversionStatusForm(fileId, format) => Vector(
      validation(requiredValidation, "fileId", fileId),
      validation(requiredValidation, "format", format)).flatten
  }
}
