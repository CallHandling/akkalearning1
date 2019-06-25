package com.callhandling.web.validators

import akka.http.scaladsl.server.Directives.{provide, reject}
import akka.http.scaladsl.server.{Rejection, Route}
import com.callhandling.web.validators.Validator.FieldErrorInfo

trait Validator[F] extends (F => Vector[FieldErrorInfo])

object Validator {
  final case class FieldErrorInfo(name: String, error: String)
  final case class FormValidationRejection(invalidFields: Vector[FieldErrorInfo]) extends Rejection

  def validateForm[F](form: F)(f: F => Route)(implicit validator: Validator[F]): Route = {
    validator(form) match {
      case Vector() => provide(form)(f)
      case errors: Vector[FieldErrorInfo] => reject(FormValidationRejection(errors))
    }
  }

  def validate[F](fieldName: String, field: F)(implicit validation: FieldValidation[F]) =
    if (validation.validate(field)) None
    else Some(FieldErrorInfo(fieldName, validation.errorMessage(fieldName)))
}
