package com.callhandling.media.io

import com.callhandling.Validation

trait IOValidation extends Validation

case object MediaNotFound extends IOValidation {
  override def errorMessage = "Media not found"
}