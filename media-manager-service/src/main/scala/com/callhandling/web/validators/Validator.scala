package com.callhandling.web.validators

import akka.http.scaladsl.server.Directives.{provide, reject}
import akka.http.scaladsl.server.Route

trait Validator[F] extends (F => Seq[FieldErrorInfo])

object Validator {
  def validateForm[F](form: F)(f: F => Route)(implicit validator: Validator[F]): Route = {
    validator(form) match {
      case Vector() => provide(form)(f)
      case errors: Vector[FieldErrorInfo] => reject(FormValidationRejection(errors))
    }
  }

  def validate[F](fieldName: String, field: F)(implicit validation: FieldValidation[F]) =
    if (validation.validate(field)) Some(FieldErrorInfo(fieldName, validation.errorMessage(fieldName)))
    else None
}
