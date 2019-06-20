package com.callhandling.http

import akka.http.scaladsl.server.Directives.{provide, reject}
import akka.http.scaladsl.server.Route
import com.callhandling.{FieldErrorInfo, FormValidationRejection, Validator}

object Validators {
  def validateForm[T](form: T)(f: T => Route)(implicit validator: Validator[T]): Route = {
    validator(form) match {
      case Nil => provide(form)(f)
      case errors: Seq[FieldErrorInfo] => reject(FormValidationRejection(errors))
    }
  }
}
