package com.callhandling.typed.persistence

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.scaladsl.EventSourcedEntity
import akka.persistence.typed.scaladsl.Effect


object FileActor {

  val entityTypeKey: EntityTypeKey[FileCommand] = EntityTypeKey[FileCommand]("FileActor")
  val MaxNumberOfShards = 1000
  def bytesToMB(bytes: Double): Double = {
    bytes * 0.000001
  }

  def shardingBehavior(entityId: String): Behavior[FileCommand] =
    EventSourcedEntity[FileCommand, FileEvent, FileState](
      entityTypeKey = entityTypeKey,
      entityId = entityId,
      emptyState = InitState(entityId),
      commandHandler,
      eventHandler)

  private def commandHandler: (FileState, FileCommand) => Effect[FileEvent, FileState] = { (state, command) =>
    state match {
      case InitState(fileId) =>
        command match {
          case cmd @ UploadInProgressCommand(byteString, replyTo) => uploadFile(UploadFile(fileId, byteString), cmd, replyTo)
          case PassivateCommand => Effect.stop()
          case _ => Effect.unhandled
        }
      case inProgressState @ InProgressState(file) =>
        command match {
          case cmd @ UploadInProgressCommand(byteString, replyTo) => {
            println("fileId: "+ file.fileId)
            println("appended inprogress byteString size: "+ bytesToMB(inProgressState.file.byteString.size))
            uploadFile(UploadFile(file.fileId, byteString), cmd, replyTo)
          }
          case UploadedFileCommand(replyTo) => uploadedFile(inProgressState, replyTo)
          case PassivateCommand => Effect.stop()
          case _ => Effect.unhandled
        }
      case _ => Effect.unhandled
    }
  }

  private def uploadFile(file: UploadFile, cmd: UploadInProgressCommand, replyTo: ActorRef[UploadDone]): Effect[FileEvent, FileState] = {
    val evt = UploadEvent(file.fileId, file)
    Effect.persist(evt).thenRun { _ =>
      println("fileId: "+ file.fileId)
      println("byteString persisted: "+ file.byteString)
      println("chunk inprogress byteString size: "+ bytesToMB(file.byteString.size))
      replyTo ! UploadDone(file.fileId)
    }
  }

  private def uploadedFile(state: InProgressState, replyTo: ActorRef[Done]): Effect[FileEvent, FileState] = {
    val evt = UploadedEvent(state.fileId)
    Effect.persist(evt).thenRun { _ =>
      println("fileId: "+ state.fileId)
      println("completed byteString size: "+ bytesToMB(state.file.byteString.size))
      replyTo ! Done
    }
  }

  private def eventHandler: (FileState, FileEvent) => FileState = { (state: FileState, event) =>
    state match {
      case InitState(_) =>
        event match {
          case UploadEvent(_, file) => InProgressState(file)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
      }
      case inProgressState: InProgressState =>
        event match {
          case UploadEvent(_, file) => inProgressState.withFile(file)
          case UploadedEvent(_) => FinishState(inProgressState.file)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case _: FinishState =>
        // no more append after uploadedEvent
        throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

}
