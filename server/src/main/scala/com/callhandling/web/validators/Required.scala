package com.callhandling.web.validators


trait Required[F] extends FieldValidation[F] {
  override def errorMessage(fileName: String) = s"$fileName is required"
}

object Required {
  def apply[F](implicit required: Required[F]): Required[F] = required

  implicit val requiredString: Required[String] = !_.isEmpty

  implicit val requiredInt: Required[Int] = _ > 0
}
