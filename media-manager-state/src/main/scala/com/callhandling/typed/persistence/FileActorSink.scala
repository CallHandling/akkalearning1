package com.callhandling.typed.persistence

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.ByteString
import com.callhandling.typed.ffprobe.JsonUtil
import com.callhandling.typed.persistence.FileActorSink.WrappedResponse

object FileActorSink {
  trait Ack
  object Ack extends Ack

  trait Protocol
  case class Init(ackTo: ActorRef[Ack]) extends Protocol
  case class Message(ackTo: ActorRef[Ack], msg: ByteString) extends Protocol
  case object Complete extends Protocol
  case class Fail(ex: Throwable) extends Protocol
  private final case class WrappedResponse(response: FileResponse) extends Protocol
}

case class FileActorSink(entityRef: EntityRef[FileCommand]) {

  def main: Behavior[FileActorSink.Protocol] =
    Behaviors.setup { context =>
      val replyTo: ActorRef[FileResponse] = context.messageAdapter(response => WrappedResponse(response))
      Behaviors.receiveMessage[FileActorSink.Protocol] {
        case FileActorSink.Init(ackTo) => {
          ackTo ! FileActorSink.Ack
          Behaviors.same
        }
        case FileActorSink.Message(ackTo, msg) => {
          val gByteString = com.google.protobuf.ByteString.copyFrom(msg.asByteBuffer)
          entityRef ! UploadInProgressCommand(gByteString, replyTo)
          ackTo ! FileActorSink.Ack
          Behaviors.same
        }
        case FileActorSink.Complete => {
          entityRef ! UploadedFileCommand(replyTo)
          entityRef ! IdleCommand
          Behaviors.same
        }
        case FileActorSink.Fail(ex) => {
          entityRef ! PassivateCommand
          Behaviors.same
        }
        case wrapped: FileActorSink.WrappedResponse =>
          wrapped.response match {
            case UploadFile(fileId, byteString) =>
              context.log.info("UploadFile Received: ")
              context.log.info("fileId: " + fileId)
              context.log.info("appended inprogress byteString size: " + bytesToMB(byteString.size))
              Behaviors.same
            case UploadedFile(fileId, byteString, fileInfo) =>
              context.log.info("UploadedFile Received: ")
              context.log.info("fileId: " + fileId)
              context.log.info("completed byteString size: " + bytesToMB(byteString.size))
              context.log.info("fileInfo: " + JsonUtil.toJson(fileInfo))
              Behaviors.same
          }
      }
    }

  def bytesToMB(bytes: Double): Double = {
    bytes * 0.000001
  }

}
