package com.callhandling.web.validators

import cats.data.ValidatedNec
import cats.implicits._
import com.callhandling.web.Form.{ConversionStatusForm, ConvertFileForm, FileIdForm, PlayForm, UploadFileForm}
import com.callhandling.web.validators.RequestValidation.{BelowMinimumLength, EmptyField}

trait FormValidator {
  type ValidationResult[A] = ValidatedNec[RequestValidation, A]
  type FormValidation[F] = F => ValidationResult[F]

  def validateForm[F](form: F)(implicit formValidation: FormValidation[F]) =
    formValidation(form)

  def validateRequired[F: Required](field: F, fieldName: String): ValidationResult[F] =
    Either.cond(
      required(field),
      field,
      EmptyField(fieldName)).toValidatedNec

  def validateMinimum[F: Minimum](field: F, fieldName: String, limit: Int): ValidationResult[F] =
    Either.cond(
      minimum(field, limit),
      field,
      BelowMinimumLength(fieldName, limit)).toValidatedNec

  def requiredId[F: Required](fileId: F) = validateRequired(fileId, "fileId")

  def requiredFormat[F: Required](format: F) = validateRequired(format, "format")

  implicit val uploadFileFormValidation: FormValidation[UploadFileForm] = {
    case UploadFileForm(description) =>
      validateMinimum(description, "description", 5).map(UploadFileForm)
  }

  implicit val convertFileFormValidation: FormValidation[ConvertFileForm] = {
    case ConvertFileForm(fileId, format, channels, sampleRate, codec) => (
      requiredId(fileId),
      validateRequired(format, "format"),
      validateRequired(channels, "channels"),
      validateRequired(sampleRate, "sampleRate"),
      validateRequired(codec, "codec")).mapN(ConvertFileForm)
  }

  implicit val fileIdFormValidation: FormValidation[FileIdForm] = { case FileIdForm(fileId) =>
    requiredId(fileId).map(FileIdForm)
  }

  implicit val conversionStatusFormValidation: FormValidation[ConversionStatusForm] = {
    case ConversionStatusForm(fileId, format) => (
      requiredId(fileId), requiredFormat(format)).mapN(ConversionStatusForm)
  }

  // TODO: Format is not required, but if it exists, check it's validity
  //  (e.g. minimum length, against available output formats, etc.).
  implicit val playFormValidation: FormValidation[PlayForm] = { case PlayForm(fileId, format) =>
    requiredId(fileId).map(PlayForm(_, format))
  }
}
