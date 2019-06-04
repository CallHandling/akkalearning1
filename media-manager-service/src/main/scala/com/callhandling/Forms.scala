package com.callhandling

import akka.http.scaladsl.server.{Rejection}


final case class FieldErrorInfo(name: String, error: String)
final case class FormValidationRejection(invalidFields: Seq[FieldErrorInfo]) extends Rejection
trait Validator[T] extends (T => Seq[FieldErrorInfo]) {
  protected def validationStage(rule: Boolean, fieldName: String, errorText: String): Option[FieldErrorInfo] =
    if (rule) Some(FieldErrorInfo(fieldName, errorText)) else None
}

object ValidationUtils {
  def isEmptyRule(s: String) = if (s.isEmpty) true else false
  def isEmptyMessage(fieldName: String) = fieldName + " must not be empty"
}


object Forms {

  case object UploadFileFormConstant {
    val File = "file"
    val Filename = "file"
    val Json = "json"
  }
  final case class UploadFileForm(description: String)
  final case class ConvertFileForm(fileId: String, format: String)

  object ConvertFileFormValidator extends Validator[ConvertFileForm] {
    override def apply(model: ConvertFileForm): Seq[FieldErrorInfo] = {
      val fileId: Option[FieldErrorInfo] = validationStage(ValidationUtils.isEmptyRule(model.fileId), "fileId",
        ValidationUtils.isEmptyMessage("fileId"))
      val format: Option[FieldErrorInfo] = validationStage(ValidationUtils.isEmptyRule(model.format), "format",
        ValidationUtils.isEmptyMessage("format"))
      (fileId :: format :: Nil).flatten
    }
  }

}
