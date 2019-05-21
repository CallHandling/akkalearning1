package com.callhandling.typed.persistence

import akka.Done
import akka.typed.ActorRef

sealed trait FileCommand extends Serializable

final case class UploadFileCommand(uploadFile: UploadFile, replyTo: ActorRef[UploadDone]) extends FileCommand
final case class UploadDone(fileId: String)

final case class UploadedFileCommand(replyTo: ActorRef[Done]) extends FileCommand

final case object PassivateFile extends FileCommand
