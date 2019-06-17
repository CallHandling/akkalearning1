package com.callhandling

import akka.http.scaladsl.server.Rejection


final case class FieldErrorInfo(name: String, error: String)
final case class FormValidationRejection(invalidFields: Seq[FieldErrorInfo]) extends Rejection
trait Validator[T] extends (T => Seq[FieldErrorInfo]) {
  protected def validation(validation: Validation[Any], fieldName: String, field: Any): Option[FieldErrorInfo] =
    if(validation.rule(field)) Some(FieldErrorInfo(fieldName, validation.errorMessage(fieldName)))
    else None
}


case class Validation[S](rule: S => Boolean, errorMessage: String => String)


object ValidationUtils {
  type AnyRule = Any => Boolean
  type ErrorMessage = String => String

  private def requiredRule: AnyRule = {
    case s : String => s.isEmpty
    case i : Int => i <= 0
    case _ => true
  }
  private def requiredMessage: String => String = _ + " is required"
  def requiredValidation = Validation[Any](requiredRule, requiredMessage)

  private def minRule(limit: Int): AnyRule = {
    case s : String => s.length < limit
    case i : Int => i < limit
    case _ => true
  }
  private def minMessage(limit: Int): ErrorMessage = _ + s" minimum chars of $limit"
  def minValidation(limit: Int) = Validation[Any](minRule(limit), minMessage(limit))
}


object Forms {

  final case class UploadFileForm(description: String)
  object UploadFileFormValidator extends Validator[UploadFileForm] {
    override def apply(model: UploadFileForm): Seq[FieldErrorInfo] = {

      val description: Option[FieldErrorInfo] =
        validation(ValidationUtils.minValidation(5), "fileId", model.description)

      (description :: Nil).flatten
    }
  }

  final case class ConvertFileForm(fileId: String, format: String, channels: Int, sampleRate: Int, codec: String)
  object ConvertFileFormValidator extends Validator[ConvertFileForm] {
    override def apply(model: ConvertFileForm): Seq[FieldErrorInfo] = {
      val fileId: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "fileId", model.fileId)
      val format: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "format", model.format)
      val channels: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "channels", model.channels)
      val sampleRate: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "sampleRate", model.sampleRate)
      val codec: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "codec", model.codec)

      (fileId :: format :: channels :: sampleRate :: codec :: Nil).flatten
    }
  }

  final case class FileIdForm(fileId: String)
  object FileIdFormValidator extends Validator[FileIdForm] {
    override def apply(model: FileIdForm): Seq[FieldErrorInfo] = {

      val fileId: Option[FieldErrorInfo] = validation(ValidationUtils.requiredValidation, "fileId", model.fileId)

      (fileId :: Nil).flatten
    }
  }

}
