package com.callhandling.typed.persistence

import akka.Done
import akka.actor.typed.ActorRef
import akka.util.ByteString


sealed trait FileCommand

final case class UploadInProgressCommand(byteString: ByteString, replyTo: ActorRef[UploadDone]) extends FileCommand
final case class UploadDone(fileId: String)

final case class UploadedFileCommand(replyTo: ActorRef[Done]) extends FileCommand

final case object PassivateCommand extends FileCommand


