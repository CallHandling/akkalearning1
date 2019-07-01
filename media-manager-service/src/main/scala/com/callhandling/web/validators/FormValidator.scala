package com.callhandling.web.validators

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNec
import cats.implicits._
import com.callhandling.web.Form._

trait FormValidator {
  type ValidationResult[A] = ValidatedNec[RequestValidation, A]
  type FormValidation[F] = F => ValidationResult[F]

  def validateForm[F, A](form: F)(f: ValidationResult[F] => A)
      (implicit formValidation: FormValidation[F]): A =
    f(formValidation(form))

  private def requiredId[F: Required](fileId: F) = validateRequired(fileId, "File")

  private def requiredFormat[F: Required](format: F) = validateRequired(format, "Format")

  implicit lazy val uploadFileFormValidation: FormValidation[UploadFileForm] = {
    case UploadFileForm(description) =>
      validateMinimum(description, "Description", 5).map(UploadFileForm)
  }

  implicit lazy val convertFileFormValidation: FormValidation[ConvertFileForm] = {
    case ConvertFileForm(fileId, format, channels, sampleRate, codec) => (
      requiredId(fileId),
      requiredFormat(format),
      validateRequired(channels, "Channels"),
      validateRequired(sampleRate, "Sample Rate"),
      validateRequired(codec, "Codec")).mapN(ConvertFileForm)
  }

  implicit lazy val fileIdFormValidation: FormValidation[FileIdForm] = { case FileIdForm(fileId) =>
    requiredId(fileId).map(FileIdForm)
  }

  implicit lazy val formatFormValidation: FormValidation[FormatForm] = { case FormatForm(format) =>
    requiredFormat(format).map(FormatForm)
  }

  implicit lazy val optionalFormatValidation: FormValidation[OptionalFormatForm] = {
    // non-existing format parameter should not be an issue
    case form @ OptionalFormatForm(None) => Valid(form)

    // validate format normally if it exists
    case OptionalFormatForm(Some(format)) => validateForm(FormatForm(format)) {
      case Valid(FormatForm(_)) => Valid(OptionalFormatForm(Some(format)))
      case invalid @ Invalid(_) => invalid
    }
  }
}
