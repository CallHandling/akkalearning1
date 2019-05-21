package com.callhandling.typed.persistence

sealed trait FileEvent extends Serializable

final case class UploadEvent(file: UploadFile) extends FileEvent

final case class UploadedEvent(fileId: String) extends FileEvent

