package com.callhandling.typed.persistence

import com.google.protobuf.ByteString

sealed trait FileState

final case class InitState(fileId: String) extends FileState

final case class InProgressState(file: UploadFile)  extends FileState {
  def withFile(newFile: UploadFile): FileState = copy(file = UploadFile(file.fileId, file.byteString.concat(newFile.byteString)))
  def fileId: String = file.fileId
}

final case class FinishState(file: UploadFile) extends FileState {
  def fileId: String = file.fileId
}

final case class UploadFile(fileId: String, byteString: ByteString)
