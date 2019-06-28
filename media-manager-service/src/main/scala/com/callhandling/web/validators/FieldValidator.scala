package com.callhandling.web.validators

import cats.implicits._
import com.callhandling.web.validators.RequestValidation.{BelowMinimumLength, EmptyField}

trait FieldValidator {
  trait Required[F] extends (F => Boolean)

  trait Minimum[F] extends ((F, Int) => Boolean)

  def required[F](field: F)(implicit req: Required[F]): Boolean =
    req(field)

  def minimum[F](field: F, limit: Int)(implicit min: Minimum[F]): Boolean =
    min(field, limit)

  def validateRequired[F: Required](field: F, fieldName: String): ValidationResult[F] =
    Either.cond(
      required(field),
      field,
      EmptyField(fieldName)).toValidatedNec

  def validateMinimum[F: Minimum](field: F, fieldName: String, limit: Int): ValidationResult[F] =
    Either.cond(
      minimum(field, limit),
      field,
      BelowMinimumLength(fieldName, limit)).toValidatedNec

  implicit val minimumString: Minimum[String] = _.length >= _

  implicit val minimumInt: Minimum[Int] = _ >= _

  implicit val requiredString: Required[String] = !_.isEmpty

  implicit val requiredInt: Required[Int] = _ > 0
}
