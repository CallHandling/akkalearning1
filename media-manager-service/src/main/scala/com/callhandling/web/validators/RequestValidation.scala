package com.callhandling.web.validators

import com.callhandling.Validation

sealed trait RequestValidation extends Validation

object RequestValidation {
  final case class EmptyField(fieldName: String) extends RequestValidation {
    override def errorMessage = s"$fieldName is empty"
  }

  final case class BelowMinimumLength(fieldName: String, limit: Int) extends RequestValidation {
    override def errorMessage = s"$fieldName is below the minimum of $limit"
  }
}
