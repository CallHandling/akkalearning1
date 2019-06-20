package com.callhandling.http

import com.callhandling.Forms.{ConvertFileForm, FileIdForm, UploadFileForm}

package object validators {
  implicit object UploadFileFormValidator extends Validator[UploadFileForm] {
    override def apply(model: UploadFileForm): Seq[FieldErrorInfo] =
      validation(ValidationUtils.minValidation(5), "fileId", model.description).toVector
  }

  implicit object ConvertFileFormValidator extends Validator[ConvertFileForm] {
    override def apply(model: ConvertFileForm): Seq[FieldErrorInfo] = {
      val fileId: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "fileId", model.fileId)
      val format: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "format", model.format)
      val channels: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "channels", model.channels)
      val sampleRate: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "sampleRate", model.sampleRate)
      val codec: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "codec", model.codec)

      (fileId :: format :: channels :: sampleRate :: codec :: Nil).flatten
    }
  }

  implicit object FileIdFormValidator extends Validator[FileIdForm] {
    override def apply(model: FileIdForm): Seq[FieldErrorInfo] =
      validation(ValidationUtils.requiredValidation, "fileId", model.fileId).toVector
  }
}
