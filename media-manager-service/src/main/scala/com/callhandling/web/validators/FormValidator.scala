package com.callhandling.web.validators

import cats.data.ValidatedNec
import cats.implicits._
import com.callhandling.web.Form.{ConversionStatusForm, ConvertFileForm, FileIdForm, FormatForm, PlayForm, UploadFileForm}
import com.callhandling.web.validators.RequestValidation.{BelowMinimumLength, EmptyField}

trait FormValidator {
  type ValidationResult[A] = ValidatedNec[RequestValidation, A]
  type FormValidation[F] = F => ValidationResult[F]

  def validateForm[F](form: F)(implicit formValidation: FormValidation[F]) =
    formValidation(form)

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

  // TODO: Check if format belongs to the supported output formats of the file
  implicit lazy val conversionStatusFormValidation: FormValidation[ConversionStatusForm] = {
    case ConversionStatusForm(fileId, format) => (
      requiredId(fileId), requiredFormat(format)).mapN(ConversionStatusForm)
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
