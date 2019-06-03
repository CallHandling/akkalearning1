package com.callhandling.typed.persistence

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.ByteString
import com.callhandling.typed.ffprobe.JsonUtil
import com.callhandling.typed.persistence.FileActorSink.{WrappedFileListResponse, WrappedFileResponse}


object FileActorSink {
  trait Ack
  object Ack extends Ack

  trait Protocol
  case class Init(ackTo: ActorRef[Ack]) extends Protocol
  case class Message(ackTo: ActorRef[Ack], msg: ByteString) extends Protocol
  case object Complete extends Protocol
  case class Fail(ex: Throwable) extends Protocol
  private final case class WrappedFileResponse(response: FileResponse) extends Protocol
  private final case class WrappedFileListResponse(response: FileListResponse) extends Protocol
}


case class FileActorSink(fileActorEntityRef: EntityRef[FileCommand], fileListActorEntityRef: EntityRef[FileListCommand]) {

  def main: Behavior[FileActorSink.Protocol] =
    Behaviors.setup { context =>
      val replyToFileActor: ActorRef[FileResponse] = context.messageAdapter(response => WrappedFileResponse(response))
      val replyToFileListActor: ActorRef[FileListResponse] = context.messageAdapter(response => WrappedFileListResponse(response))
      Behaviors.receiveMessage[FileActorSink.Protocol] {
        case FileActorSink.Init(ackTo) => {
          ackTo ! FileActorSink.Ack
          Behaviors.same
        }
        case FileActorSink.Message(ackTo, msg) => {
          val gByteString = com.google.protobuf.ByteString.copyFrom(msg.asByteBuffer)
          fileActorEntityRef ! UploadInProgressCommand(gByteString, replyToFileActor)
          ackTo ! FileActorSink.Ack
          Behaviors.same
        }
        case FileActorSink.Complete => {
          fileActorEntityRef ! UploadedFileCommand(replyToFileActor)
          fileActorEntityRef ! PassivateFileCommand
          Behaviors.same
        }
        case FileActorSink.Fail(ex) => {
          fileActorEntityRef ! PassivateFileCommand
          Behaviors.same
        }
        case wrapped: FileActorSink.WrappedFileResponse => {
          wrapped.response match {
            case UploadFile(fileId, byteString) =>
              context.log.info("UploadFile Received: ")
              context.log.info("fileId: " + fileId)
              context.log.info("appended inprogress byteString size: " + bytesToMB(byteString.size))
              Behaviors.same
            case uploadedFile@UploadedFile(fileId, byteString, fileInfo) =>
              context.log.info("UploadedFile Received: ")
              context.log.info("fileId: " + fileId)
              context.log.info("completed byteString size: " + bytesToMB(byteString.size))
              context.log.info("fileInfo: " + JsonUtil.toJson(fileInfo))
              fileListActorEntityRef ! AddFileListCommand(uploadedFile, replyToFileListActor)
              fileListActorEntityRef ! GetFileListCommand(fileId, replyToFileListActor)
              Behaviors.same
          }
        }
        case wrapped: FileActorSink.WrappedFileListResponse => {
          wrapped.response match {
            case AddFile(fileId) =>
              context.log.info("Added file to FileListActor:")
              context.log.info("fileId: " + fileId)
              Behaviors.same
            case ReturnFile(fileId, uploadedFile) =>
              context.log.info("Retrieve file from FileListActor:")
              context.log.info("fileId: " + fileId)
              context.log.info("uploadedFile mediaInfo: "+ uploadedFile.map(a => JsonUtil.toJson(a.multimediaFileInfo)))
              Behaviors.same
          }
        }
      }
    }

  def bytesToMB(bytes: Double): Double = {
    bytes * 0.000001
  }

}
