package com.callhandling.web.validators

import akka.http.scaladsl.server.Rejection
import com.callhandling.web.validators.Validator.validation

final case class FieldErrorInfo(name: String, error: String)
final case class FormValidationRejection(invalidFields: Seq[FieldErrorInfo]) extends Rejection

case class Validation[S](rule: S => Boolean, errorMessage: String => String)

object ValidationUtils {
  // TODO: Improve the type-safety
  type AnyRule = Any => Boolean
  type ErrorMessage = String => String

  // TODO: Improve the type-safety
  private def requiredRule: AnyRule = {
    case s : String => s.isEmpty
    case i : Int => i <= 0
    case _ => true
  }
  private def requiredMessage: String => String = _ + " is required"

  // TODO: Improve the type-safety
  def requiredValidation = Validation[Any](requiredRule, requiredMessage)

  // TODO: Improve the type-safety
  private def minRule(limit: Int): AnyRule = {
    case s : String => s.length < limit
    case i : Int => i < limit
    case _ => true
  }
  private def minMessage(limit: Int): ErrorMessage = _ + s" minimum chars of $limit"
  def minValidation(limit: Int) = Validation[Any](minRule(limit), minMessage(limit))

  def withRequiredId(id: String, validations: Option[FieldErrorInfo]*): Vector[FieldErrorInfo] =
    (validation(requiredValidation, "fileId", id) +: validations.toVector).flatten
}
