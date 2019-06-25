package com.callhandling.web.validators

trait Minimum[F] extends FieldValidation[F] {
  def limit: Int
}

object Minimum {
  def apply[F](implicit minimum: Minimum[F]): Minimum[F] = minimum

  implicit val minimumString: Minimum[String] = new Minimum[String] {
    // Default limitation for the number of characters
    override def limit = 5

    override def validate(field: String) = field.length < limit

    override def errorMessage(fieldName: String): String =
      s"$fieldName should be at least $limit characters long."
  }
}
