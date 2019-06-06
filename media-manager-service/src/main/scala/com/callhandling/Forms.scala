package com.callhandling

import akka.http.scaladsl.server.{Rejection}


final case class FieldErrorInfo(name: String, error: String)
final case class FormValidationRejection(invalidFields: Seq[FieldErrorInfo]) extends Rejection
trait Validator[T] extends (T => Seq[FieldErrorInfo]) {
  protected def validation(validation: Validation[String], fieldName: String, field: String): Option[FieldErrorInfo] = {
    if(validation.rule(field)) Some(FieldErrorInfo(fieldName, validation.errorMessage(fieldName))) else None
  }
  protected def validation2(validation: Validation[(String, Int)], fieldName: String, field: String, limit: Int): Option[FieldErrorInfo] = {
    if(validation.rule((field, limit))) Some(FieldErrorInfo(fieldName, validation.errorMessage((fieldName, limit)))) else None
  }
}

case class Validation[S](rule: S => Boolean, errorMessage: S => String)
object ValidationUtils {
  private def requiredRule(s: String) = if (s.nonEmpty) false else true
  private def requiredMessage(fieldName: String) = fieldName + " is required"
  def requiredValidation = Validation[String](requiredRule, requiredMessage)

  private def minRule(s: (String, Int)) = if (s._1.size >= s._2 ) false else true
  private def minMessage(s: (String, Int)) = s._1 + " minimum chars of " + s._2
  def minValidation = Validation[(String, Int)](minRule, minMessage)
}

object Forms {

  final case class UploadFileForm(description: String)
  object UploadFileFormValidator extends Validator[UploadFileForm] {
    override def apply(model: UploadFileForm): Seq[FieldErrorInfo] = {

      val description: Option[FieldErrorInfo] = validation2(ValidationUtils.minValidation, "fileId", model.description, 5)

      (description :: Nil).flatten
    }
  }

  final case class ConvertFileForm(fileId: String, format: String)
  object ConvertFileFormValidator extends Validator[ConvertFileForm] {
    override def apply(model: ConvertFileForm): Seq[FieldErrorInfo] = {

      val fileId: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "fileId", model.fileId)
      val format: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "format", model.format)

      (fileId :: format :: Nil).flatten
    }
  }

}
