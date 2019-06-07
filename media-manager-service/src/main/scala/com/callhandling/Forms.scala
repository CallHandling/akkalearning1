package com.callhandling

import akka.http.scaladsl.server.{Rejection}


final case class FieldErrorInfo(name: String, error: String)
final case class FormValidationRejection(invalidFields: Seq[FieldErrorInfo]) extends Rejection
trait Validator[T] extends (T => Seq[FieldErrorInfo]) {
  protected def validation(validation: Validation[String], fieldName: String, field: String): Option[FieldErrorInfo] =
    if(validation.rule(field)) Some(FieldErrorInfo(fieldName, validation.errorMessage(fieldName)))
    else None
}

case class Validation[S](rule: S => Boolean, errorMessage: S => String)

object ValidationUtils {
  type StringValidator = String => Boolean
  type ValidatorMessage = String => String

  private def requiredRule: StringValidator = _.isEmpty
  private def requiredMessage: ValidatorMessage = _ + " is required"

  def requiredValidation = Validation[String](requiredRule, requiredMessage)

  private def minRule(limit: Int): StringValidator = _.length < limit
  private def minMessage(limit: Int): ValidatorMessage = _ + s" minimum chars of $limit"
  def minValidation(limit: Int) = Validation[String](minRule(limit), minMessage(limit))
}

object Forms {
  final case class UploadFileForm(description: String)

  object UploadFileFormValidator extends Validator[UploadFileForm] {
    override def apply(model: UploadFileForm): Seq[FieldErrorInfo] = {

      val description: Option[FieldErrorInfo] = validation(ValidationUtils.minValidation(5), "fileId", model.description)

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
