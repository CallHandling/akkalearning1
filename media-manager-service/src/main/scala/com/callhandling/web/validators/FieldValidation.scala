package com.callhandling.web.validators

import akka.http.scaladsl.server.Rejection
import com.callhandling.web.validators.Validator.validate

final case class FieldErrorInfo(name: String, error: String)
final case class FormValidationRejection(invalidFields: Seq[FieldErrorInfo]) extends Rejection

//case class Validation[S](rule: S => Boolean, errorMessage: String => String)

trait FieldValidation[F] {
  def validate(field: F): Boolean
  def errorMessage(fieldName: String): String
}

/*
object ValidationUtils {
  // TODO: Improve the type-safety
  type Rule[A] = A => Boolean
  type ErrorMessage = String => String

  // TODO: Improve the type-safety
  private def requiredRule[A]: Rule[A] = {
    case s : String => s.isEmpty
    case i : Int => i <= 0
    case _ => true
  }

  private def requiredMessage: String => String = _ + " is required"

  // TODO: Improve the type-safety
  def requiredValidation[A] = Validation[A](requiredRule[A], requiredMessage)

  // TODO: Improve the type-safety
  private def minRule(limit: Int): Rule = {
    case s : String => s.length < limit
    case i : Int => i < limit
    case _ => true
  }
  private def minMessage(limit: Int): ErrorMessage = _ + s" minimum chars of $limit"
  def minValidation(limit: Int) = Validation[Any](minRule(limit), minMessage(limit))

  def withRequiredId(id: String, validations: Option[FieldErrorInfo]*): Vector[FieldErrorInfo] =
    (validateField(requiredValidation, "fileId", id) +: validations.toVector).flatten
}
*/