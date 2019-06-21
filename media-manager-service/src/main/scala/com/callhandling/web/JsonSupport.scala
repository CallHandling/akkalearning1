package com.callhandling.web

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import Forms.{ConvertFileForm, FileIdForm, UploadFileForm}
import com.callhandling.Service.{FileIdResult, UploadResult}
import com.callhandling.web.validators.FieldErrorInfo
import com.callhandling.media.MediaStream.{AspectRatio, Bits, Channel, Codec, Color, Dimensions, FrameRates, Nb, Samples, Time}
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.converters.{OnGoing, OutputArgs}
import com.callhandling.media.{MediaStream, Rational}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  type RJF[A] = RootJsonFormat[A]

  implicit val rationalFormat: RJF[Rational] = jsonFormat2(Rational)
  implicit val codecFormat: RJF[Codec] = jsonFormat5(Codec)
  implicit val aspectRationFormat: RJF[AspectRatio] = jsonFormat2(AspectRatio)
  implicit val colorFormat: RJF[Color] = jsonFormat4(Color)
  implicit val dimensionsFormat: RJF[Dimensions] = jsonFormat2(Dimensions)
  implicit val bitsFormat: RJF[Bits] = jsonFormat4(Bits)
  implicit val nbFormat: RJF[Nb] = jsonFormat3(Nb)
  implicit val samplesFormat: RJF[Samples] = jsonFormat2(Samples)
  implicit val frameRatesFormat: RJF[FrameRates] = jsonFormat2(FrameRates)
  implicit val timeFormat: RJF[Time] = jsonFormat6(Time)
  implicit val channelFormat: RJF[Channel] = jsonFormat2(Channel)
  implicit val streamDetailsFormat: RJF[MediaStream] = jsonFormat21(MediaStream.apply)
  implicit val fileFormatFormat: RJF[Format] = jsonFormat2(Format)
  implicit val outputArgsFormat: RJF[OutputArgs] = jsonFormat4(OutputArgs)

  implicit val fileIdResultFormat: RJF[FileIdResult] = jsonFormat1(FileIdResult)
  implicit val uploadResultFormat: RJF[UploadResult] = jsonFormat5(UploadResult)

  implicit val uploadFileFormFormat: RJF[UploadFileForm] = jsonFormat1(UploadFileForm)
  implicit val convertFileFormFormat: RJF[ConvertFileForm] = jsonFormat5(ConvertFileForm)
  implicit val fileIdFormFormat: RJF[FileIdForm] = jsonFormat1(FileIdForm)
  implicit val conversionProgressFormat: RJF[OnGoing] = jsonFormat10(OnGoing)

  implicit val validatedFieldFormat: RJF[FieldErrorInfo] = jsonFormat2(FieldErrorInfo)
}
