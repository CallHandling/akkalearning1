package com.callhandling.web.validators

trait FieldValidator {
  trait Required[F] extends (F => Boolean)

  trait Minimum[F] extends ((F, Int) => Boolean)

  def required[F](field: F)(implicit req: Required[F]): Boolean =
    req(field)

  def minimum[F](field: F, limit: Int)(implicit min: Minimum[F]): Boolean =
    min(field)

  implicit val minimumString: Minimum[String] = _.length > _

  implicit val minimumInt: Minimum[Int] = _ >= _

  implicit val requiredString: Required[String] = !_.isEmpty

  implicit val requiredInt: Required[Int] = _ > 0
}
