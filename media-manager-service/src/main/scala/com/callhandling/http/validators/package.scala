package com.callhandling.http

import com.callhandling.Forms.{ConvertFileForm, FileIdForm, UploadFileForm}
import com.callhandling.http.validators.Validator._

package object validators {
  implicit val uploadFileFormValidator: Validator[UploadFileForm] = model =>
    validation(ValidationUtils.minValidation(5), "fileId", model.description).toVector

  implicit val convertFileFormValidator: Validator[ConvertFileForm] = {
    case ConvertFileForm(fileId, format, channels, sampleRate, codec) => Vector(
      validation(ValidationUtils.requiredValidation, "fileId", fileId),
      validation(ValidationUtils.requiredValidation, "format", format),
      validation(ValidationUtils.requiredValidation, "channels", channels),
      validation(ValidationUtils.requiredValidation, "sampleRate", sampleRate),
      validation(ValidationUtils.requiredValidation, "codec", codec)).flatten
  }

  implicit val fileIdFormValidator: Validator[FileIdForm] = model =>
    validation(ValidationUtils.requiredValidation, "fileId", model.fileId).toVector
}
