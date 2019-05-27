package com.callhandling.typed.persistence

sealed trait FileEvent

final case class UploadEvent(fileId: String, file: UploadFile) extends FileEvent

final case class UploadedEvent(fileId: String) extends FileEvent

