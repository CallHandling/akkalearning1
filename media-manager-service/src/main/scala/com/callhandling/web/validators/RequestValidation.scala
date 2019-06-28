package com.callhandling.web.validators

import com.callhandling.Validation

sealed trait RequestValidation extends Validation

object RequestValidation {
  final case class EmptyField(fieldName: String) extends RequestValidation {
    override def errorMessage = s"Field $fieldName is empty"
  }

  final case class BelowMinimumLength(fieldName: String, limit: Int) extends RequestValidation {
    override def errorMessage = s"Field $fieldName should be at least $limit characters long"
  }
}
