package com.callhandling.typed.persistence

import akka.Done
import akka.typed.Behavior
import akka.typed.cluster.sharding.EntityTypeKey
import akka.typed.persistence.scaladsl.PersistentActor
import akka.typed.persistence.scaladsl.PersistentActor._

object FileActor {

  val ShardingTypeName = EntityTypeKey[FileCommand]("FileActor")
  val MaxNumberOfShards = 1000

  /**
    * An ordinary persistent actor with a fixed persistenceId,
    * see alternative `shardingBehavior` below.
    */
  def behavior: Behavior[FileCommand] =
    PersistentActor.immutable[FileCommand, FileEvent, FileState](
      persistenceId = "abc",
      initialState = FileState.empty,
      commandHandler,
      eventHandler)

  /**
    * Persistent actor in Cluster Sharding, when the persistenceId is not known
    * until the actor is started and typically based on the entityId, which
    * is the actor name.
    */
  def shardingBehavior: Behavior[FileCommand] =
    PersistentActor.persistentEntity[FileCommand, FileEvent, FileState](
      persistenceIdFromActorName = name => ShardingTypeName.name + "-" + name,
      initialState = FileState.empty,
      commandHandler,
      eventHandler)

  private def commandHandler: CommandHandler[FileCommand, FileEvent, FileState] = CommandHandler.byState {
    case state if state.isEmpty  ⇒ initial
    case state if !state.isEmpty ⇒ uploadFile
  }

  private def initial: CommandHandler[FileCommand, FileEvent, FileState] =
    CommandHandler { (ctx, state, cmd) ⇒
      cmd match {
        case UploadFileCommand(f, replyTo) ⇒
          val evt = UploadEvent(f)
          Effect.persist(evt).andThen { state2 ⇒
            // After persist is done additional side effects can be performed
            replyTo ! UploadDone(f.fileId)
          }
        case PassivateFile =>
          Effect.stop
        case _ ⇒
          Effect.unhandled
      }
    }

  private def uploadFile: CommandHandler[FileCommand, FileEvent, FileState] = {
    CommandHandler { (ctx, state, cmd) ⇒
      cmd match {
        case UploadedFileCommand(replyTo) ⇒
          Effect.persist(UploadedEvent(state.id)).andThen { _ ⇒
            println(s"File post ${state.id} was published")
            replyTo ! Done
          }
        case _: UploadedFileCommand ⇒
          Effect.unhandled
        case PassivateFile =>
          Effect.stop
      }
    }
  }

  private def eventHandler(state: FileState, event: FileEvent): FileState =
    event match {
      case UploadEvent(file) ⇒
        state.withFile(file)

      case UploadedEvent(_) ⇒
        state.copy(isUploaded = true)
    }
}
