package com.callhandling.typed.cluster

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.ByteString
import com.callhandling.typed.cluster.FileActorSink.{InternalDone, InternalUploadDone}
import com.callhandling.typed.persistence.{FileCommand, IdleCommand, InitCommand, PassivateCommand, UploadDone, UploadInProgressCommand, UploadedFileCommand}

object FileActorSink {
  trait Ack
  object Ack extends Ack

  trait Protocol
  case class Init(ackTo: ActorRef[Ack]) extends Protocol
  case class Message(ackTo: ActorRef[Ack], msg: ByteString) extends Protocol
  case object Complete extends Protocol
  case class Fail(ex: Throwable) extends Protocol
  private case class InternalUploadDone(fileId: String) extends Protocol
  private case object InternalDone extends Protocol
}

case class FileActorSink(entityRef: EntityRef[FileCommand]) {

  def main: Behavior[FileActorSink.Protocol] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage[FileActorSink.Protocol] {
        case FileActorSink.Init(ackTo) => {
          context.log.debug("init")
          ackTo ! FileActorSink.Ack
          Behaviors.same
        }
        case FileActorSink.Message(ackTo, msg) => {
          val replyTo: ActorRef[UploadDone] = context.messageAdapter(reply => InternalUploadDone(reply.fileId))
          val gByteString = com.google.protobuf.ByteString.copyFrom(msg.asByteBuffer)
          entityRef ! UploadInProgressCommand(gByteString, replyTo)
          context.log.debug("message " + gByteString)
          ackTo ! FileActorSink.Ack
          Behaviors.same
        }
        case FileActorSink.Complete => {
          val replyTo: ActorRef[Done] = context.messageAdapter(_ => InternalDone)
          entityRef ! UploadedFileCommand(replyTo)
          context.log.debug("complete")
          entityRef ! InitCommand
          entityRef ! IdleCommand
          Behaviors.same
        }
        case FileActorSink.Fail(ex) => {
          entityRef ! PassivateCommand
          context.log.debug("fail")
          Behaviors.same
        }
      }
    }

}
