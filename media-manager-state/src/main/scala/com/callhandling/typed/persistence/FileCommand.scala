package com.callhandling.typed.persistence

import akka.Done
import akka.actor.typed.ActorRef
import com.google.protobuf.ByteString


sealed trait FileCommand

final case object InitCommand extends FileCommand
final case object IdleCommand extends FileCommand
final case object PassivateCommand extends FileCommand

final case class UploadInProgressCommand(byteString: ByteString, replyTo: ActorRef[UploadDone]) extends FileCommand
final case class UploadDone(fileId: String)

final case class UploadedFileCommand(replyTo: ActorRef[Done]) extends FileCommand


