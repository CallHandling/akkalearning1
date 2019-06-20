package com.callhandling.http.validators

import akka.http.scaladsl.server.Rejection

final case class FieldErrorInfo(name: String, error: String)
final case class FormValidationRejection(invalidFields: Seq[FieldErrorInfo]) extends Rejection

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
