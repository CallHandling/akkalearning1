package com.callhandling.http.validators

import akka.http.scaladsl.server.Directives.{provide, reject}
import akka.http.scaladsl.server.Route

trait Validator[T] extends (T => Seq[FieldErrorInfo])

object Validator {
  def validateForm[T](form: T)(f: T => Route)(implicit validator: Validator[T]): Route = {
    validator(form) match {
      case Nil => provide(form)(f)
      case errors: Seq[FieldErrorInfo] => reject(FormValidationRejection(errors))
    }
  }

  def validation(validation: Validation[Any], fieldName: String, field: Any): Option[FieldErrorInfo] =
    if(validation.rule(field)) Some(FieldErrorInfo(fieldName, validation.errorMessage(fieldName)))
    else None
}
