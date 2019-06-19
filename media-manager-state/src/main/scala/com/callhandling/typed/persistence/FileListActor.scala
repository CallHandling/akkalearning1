package com.callhandling.typed.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey, EventSourcedEntity}
import akka.cluster.typed.{Cluster, Join}
import akka.persistence.typed.scaladsl.Effect
import com.callhandling.typed.cluster.ActorSharding
import com.typesafe.config.Config

import scala.concurrent.duration._


sealed trait FileListCommand
final case object IdleFileListCommand extends FileListCommand
final case object PassivateFileListCommand extends FileListCommand
final case class AddFileListCommand(uploadedFile: UploadedFile, replyTo: ActorRef[AddFile]) extends FileListCommand
final case class GetFileListCommand(fileId: String, replyTo: ActorRef[ReturnFile]) extends FileListCommand

sealed trait FileListEvent
final case class AddEvent(fileId: String, file: UploadedFile) extends FileListEvent
final case class GetEvent(fileId: String) extends FileListEvent

sealed trait FileListState
final case class InitFileListState(fileId: String) extends FileListState
final case class StorageState(fileMap: Map[String, UploadedFile])  extends FileListState {
  def withFile(newFile: UploadedFile): FileListState = copy(fileMap = fileMap + (newFile.fileId -> newFile))
}

sealed trait FileListResponse
final case class AddFile(fileId: String) extends FileListResponse
final case class ReturnFile(fileId: String, file: Option[UploadedFile]) extends FileListResponse


object FileListActor extends ActorSharding[FileListCommand] {

  val entityTypeKey: EntityTypeKey[FileListCommand] = EntityTypeKey[FileListCommand]("FileListActor")

  override def shardingCluster(clusterName: String, config: Config): ClusterSharding = {
    val system = ActorSystem(Behaviors.empty[FileListCommand], clusterName, config)
    val sharding = ClusterSharding(system)
    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(
      Entity(
        typeKey = entityTypeKey,
        createBehavior = entityContext => shardingBehavior(entityContext.shard, entityContext.entityId))
        .withStopMessage(PassivateFileListCommand))
    sharding
  }

  override def shardingBehavior(shard: ActorRef[ClusterSharding.ShardCommand], entityId: String): Behavior[FileListCommand] = {
    Behaviors.setup { context =>
      def behavior: Behavior[FileListCommand] =
          EventSourcedEntity[FileListCommand, FileListEvent, FileListState](
            entityTypeKey = entityTypeKey,
            entityId = entityId,
            emptyState = InitFileListState(entityId),
            commandHandler(context, shard),
            eventHandler(context))
      context.setReceiveTimeout(30.seconds, IdleFileListCommand)
      behavior
    }
  }

  private def commandHandler(context: ActorContext[FileListCommand], shard: ActorRef[ClusterSharding.ShardCommand]):
    (FileListState, FileListCommand) => Effect[FileListEvent, FileListState] = { (state, command) =>
    state match {
      case InitFileListState(_) =>
        command match {
          case AddFileListCommand(uploadedFile, replyTo) => addFile(uploadedFile, replyTo)
          case IdleFileListCommand => ActorSharding.passivateCluster(context, shard)
          case PassivateFileListCommand => ActorSharding.passivateActor
          case _ => Effect.unhandled
        }
      case StorageState(fileMap) =>
        command match {
          case GetFileListCommand(fileId, replyTo) => getFile(fileMap, fileId, replyTo)
          case IdleFileListCommand => ActorSharding.passivateCluster(context, shard)
          case PassivateFileListCommand => ActorSharding.passivateActor
          case _ => Effect.unhandled
        }
      case _ => Effect.unhandled
    }
  }

  private def addFile(file: UploadedFile, replyTo: ActorRef[AddFile]): Effect[FileListEvent, FileListState] = {
    val evt = AddEvent(file.fileId, file)
    Effect.persist(evt).thenRun { _ =>
      replyTo ! AddFile(file.fileId)
    }
  }

  private def getFile(fileMap: Map[String, UploadedFile], fileId: String, replyTo: ActorRef[ReturnFile]): Effect[FileListEvent, FileListState] = {
    val uploadedFile = fileMap.get(fileId)
    replyTo ! ReturnFile(fileId, uploadedFile)
    Effect.none
  }

  private def eventHandler(context: ActorContext[FileListCommand]): (FileListState, FileListEvent) => FileListState = { (state: FileListState, event) =>
    state match {
      case InitFileListState(_) =>
        event match {
          case AddEvent(_, file) => StorageState(Map(file.fileId -> file))
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
      }
      case storageState: StorageState =>
        event match {
          case AddEvent(_, file) => storageState.withFile(file)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case _ =>
        throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

}
