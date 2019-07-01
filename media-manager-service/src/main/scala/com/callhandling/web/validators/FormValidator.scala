package com.callhandling.web.validators

import cats.data.ValidatedNec
import cats.implicits._
import com.callhandling.web.Form._

trait FormValidator {
  type ValidationResult[A] = ValidatedNec[RequestValidation, A]
  type FormValidation[F] = F => ValidationResult[F]

  def validateForm[F, A](form: F)(f: ValidationResult[F] => A)(implicit formValidation: FormValidation[F]): A =
    f(formValidation(form))

  def requiredId[F: Required](fileId: F) = validateRequired(fileId, "fileId")

  def requiredFormat[F: Required](format: F) = validateRequired(format, "format")

  implicit lazy val uploadFileFormValidation: FormValidation[UploadFileForm] = {
    case UploadFileForm(description) =>
      validateMinimum(description, "description", 5).map(UploadFileForm)
  }

  implicit lazy val convertFileFormValidation: FormValidation[ConvertFileForm] = {
    case ConvertFileForm(fileId, format, channels, sampleRate, codec) => (
      requiredId(fileId),
      validateRequired(format, "format"),
      validateRequired(channels, "channels"),
      validateRequired(sampleRate, "sampleRate"),
      validateRequired(codec, "codec")).mapN(ConvertFileForm)
  }

  implicit lazy val fileIdFormValidation: FormValidation[FileIdForm] = { case FileIdForm(fileId) =>
    requiredId(fileId).map(FileIdForm)
  }

  implicit lazy val formatFormValidation: FormValidation[FormatForm] = { case FormatForm(format) =>
    requiredFormat(format).map(FormatForm)
  }

  // TODO: Format is not required, but if provided, check it's validity
  //  (e.g. minimum length, converted media exists, etc.).
  implicit lazy val playFormValidation: FormValidation[PlayForm] = { case PlayForm(fileId, format) =>
    requiredId(fileId).map(PlayForm(_, format))
  }
}
