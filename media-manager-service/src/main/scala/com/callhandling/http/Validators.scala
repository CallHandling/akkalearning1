package com.callhandling.http

import akka.http.scaladsl.server.Directives.{provide, reject}
import akka.http.scaladsl.server.Route
import com.callhandling.Forms.{ConvertFileForm, FileIdForm, UploadFileForm}
import com.callhandling.{FieldErrorInfo, FormValidationRejection, ValidationUtils, Validator}

object Validators {
  def validateForm[T](form: T)(f: T => Route)(implicit validator: Validator[T]): Route = {
    validator(form) match {
      case Nil => provide(form)(f)
      case errors: Seq[FieldErrorInfo] => reject(FormValidationRejection(errors))
    }
  }

  implicit object UploadFileFormValidator extends Validator[UploadFileForm] {
    override def apply(model: UploadFileForm): Seq[FieldErrorInfo] = {

      val description: Option[FieldErrorInfo] =
        validation(ValidationUtils.minValidation(5), "fileId", model.description)

      (description :: Nil).flatten
    }
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
    override def apply(model: FileIdForm): Seq[FieldErrorInfo] = {

      val fileId: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "fileId", model.fileId)

      (fileId :: Nil).flatten
    }
  }
}
