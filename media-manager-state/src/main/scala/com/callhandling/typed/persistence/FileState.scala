package com.callhandling.typed.persistence

import akka.protobuf.ByteString
import akka.stream.scaladsl.Source

sealed trait FileStatus
case object StartStatus extends FileStatus
case object InProgressStatus extends FileStatus
case object FailStatus extends FileStatus
case object FinishStatus extends FileStatus

object FileState {
  val empty = FileState(None, isUploaded = false)
}

final case class FileState(file: Option[UploadFile], isUploaded: Boolean) {
  def withFile(newFile: UploadFile): FileState = copy(file = Some(newFile))

  def isEmpty: Boolean = file.isEmpty

  def id: String = file match {
    case Some(f) => f.fileId
    case None => throw new IllegalStateException("id unknown before UploadFile is created")
  }
}

final case class UploadFile(fileId: String, fileStream: Source[ByteString, Any])