package com.callhandling.web.validators

trait FieldValidation[F] {
  def validate(field: F): Boolean

  def errorMessage(fieldName: String): String
}