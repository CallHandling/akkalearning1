package com.callhandling.http

import com.callhandling.Forms.{ConvertFileForm, FileIdForm, UploadFileForm}
import com.callhandling.http.validators.Validator._

package object validators {
  implicit val uploadFileFormValidator: Validator[UploadFileForm] = (model: UploadFileForm) =>
    validation(ValidationUtils.minValidation(5), "fileId", model.description).toVector

  implicit val convertFileFormValidator: Validator[ConvertFileForm] = (model: ConvertFileForm) => {
    val fileId: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "fileId", model.fileId)
    val format: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "format", model.format)
    val channels: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "channels", model.channels)
    val sampleRate: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "sampleRate", model.sampleRate)
    val codec: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "codec", model.codec)

    (fileId :: format :: channels :: sampleRate :: codec :: Nil).flatten
  }

  implicit val fileIdFormValidator: Validator[FileIdForm] = (model: FileIdForm) =>
    validation(ValidationUtils.requiredValidation, "fileId", model.fileId).toVector
}
