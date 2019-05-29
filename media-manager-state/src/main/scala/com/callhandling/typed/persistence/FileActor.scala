package com.callhandling.typed.persistence

import java.util.UUID

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey, EventSourcedEntity}
import akka.persistence.typed.scaladsl.{Effect}
import scala.concurrent.duration._


object FileActor {

  val entityTypeKey: EntityTypeKey[FileCommand] = EntityTypeKey[FileCommand]("FileActor")
  val MaxNumberOfShards = 1000
  def bytesToMB(bytes: Double): Double = {
    bytes * 0.000001
  }

  def shardingBehavior(shard: ActorRef[ClusterSharding.ShardCommand], entityId: String): Behavior[FileCommand] =
//    EventSourcedEntity[FileCommand, FileEvent, FileState](
//      entityTypeKey = entityTypeKey,
//      entityId = entityId,
//      emptyState = InitState(entityId),
//      commandHandler(shard),
//      eventHandler)
//    EventSourcedBehavior[FileCommand, FileEvent, FileState](
//      persistenceId = PersistenceId(s"File-$entityId"),
//      emptyState = InitState(entityId),
//      commandHandler,
//      eventHandler)
  {
    Behaviors.setup { context =>
      def behavior: Behavior[FileCommand] =
          EventSourcedEntity[FileCommand, FileEvent, FileState](
            entityTypeKey = entityTypeKey,
            entityId = entityId,
            emptyState = InitState(entityId),
            commandHandler(context, shard),
            eventHandler(context))

      context.setReceiveTimeout(30.seconds, IdleCommand)
      behavior
    }
  }

  private def commandHandler(context: ActorContext[FileCommand], shard: ActorRef[ClusterSharding.ShardCommand]): (FileState, FileCommand) => Effect[FileEvent, FileState] = { (state, command) =>
    state match {
      case InitState(fileId) =>
        command match {
          case cmd @ UploadInProgressCommand(byteString, replyTo) => uploadFile(context, UploadFile(fileId, byteString), cmd, replyTo)
          case IdleCommand => {
            shard ! ClusterSharding.Passivate(context.self)
            Effect.none
          }
          case PassivateCommand => {
            Effect.stop()
          }
          case _ => Effect.unhandled
        }
      case inProgressState @ InProgressState(file) =>
        command match {
          case cmd @ UploadInProgressCommand(byteString, replyTo) => {
            context.log.debug("fileId: "+ file.fileId)
            context.log.debug("appended inprogress byteString size: "+ bytesToMB(inProgressState.file.byteString.size))
            uploadFile(context, UploadFile(file.fileId, byteString), cmd, replyTo)
          }
          case UploadedFileCommand(replyTo) => uploadedFile(context, inProgressState, replyTo)
          case IdleCommand => {
            shard ! ClusterSharding.Passivate(context.self)
            Effect.none
          }
          case PassivateCommand => {
            Effect.stop()
          }
          case _ => Effect.unhandled
        }
      case FinishState(_) =>
        command match {
          case InitCommand => {
            Effect.persist(InitEvent)
          }
          case IdleCommand => {
            shard ! ClusterSharding.Passivate(context.self)
            Effect.none
          }
          case PassivateCommand => {
            Effect.stop()
          }
          case _ => Effect.unhandled
        }
      case _ => Effect.unhandled
    }
  }

  private def uploadFile(context: ActorContext[FileCommand], file: UploadFile, cmd: UploadInProgressCommand, replyTo: ActorRef[UploadDone]): Effect[FileEvent, FileState] = {
    val evt = UploadEvent(file.fileId, file)
    Effect.persist(evt).thenRun { _ =>
      context.log.debug("fileId: "+ file.fileId)
      context.log.debug("byteString persisted: "+ file.byteString)
      context.log.debug("chunk inprogress byteString size: "+ bytesToMB(file.byteString.size))
      replyTo ! UploadDone(file.fileId)
    }
  }

  private def uploadedFile(context: ActorContext[FileCommand], state: InProgressState, replyTo: ActorRef[Done]): Effect[FileEvent, FileState] = {
    val evt = UploadedEvent(state.fileId)
    Effect.persist(evt).thenRun { _ =>
      context.log.debug("fileId: "+ state.fileId)
      context.log.debug("completed byteString size: "+ bytesToMB(state.file.byteString.size))
      replyTo ! Done
    }
  }

  private def eventHandler(context: ActorContext[FileCommand]): (FileState, FileEvent) => FileState = { (state: FileState, event) =>
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
      case FinishState(_) =>
        event match {
          case InitEvent => {
            InitState(UUID.randomUUID().toString)
          }
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case _ =>
        // no more append after uploadedEvent
        throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

}
