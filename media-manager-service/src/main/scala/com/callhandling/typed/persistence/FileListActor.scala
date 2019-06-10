package com.callhandling.typed.persistence


import java.nio.file.{Path, Paths}

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey, EventSourcedEntity}
import akka.cluster.typed.{Cluster, Join}
import akka.persistence.typed.scaladsl.Effect
import com.callhandling.Forms.UploadFileForm
import com.callhandling.actors.FileActor.Details
import com.callhandling.media.Formats.Format
import com.callhandling.media.{Converter, StreamDetails}
import com.callhandling.typed.cluster.ActorSharding
import com.typesafe.config.Config

import scala.concurrent.duration._


sealed trait FileListCommand
final case object IdleFileListCommand extends FileListCommand
final case object PassivateFileListCommand extends FileListCommand
final case class AddFormCommand(fileId: String, form: UploadFileForm, replyTo: ActorRef[AddFile]) extends FileListCommand
final case class AddFileCommand(fileId: String, filePath: String, fileName: String, replyTo: ActorRef[GetFile]) extends FileListCommand
final case class GetFileCommand(fileId: String, replyTo: ActorRef[GetFile]) extends FileListCommand

sealed trait FileListEvent
final case class AddFormEvent(fileId: String, form: UploadFileForm) extends FileListEvent
final case class AddFileEvent(fileId: String, file: UploadedFile) extends FileListEvent
final case class GetFileEvent(fileId: String) extends FileListEvent

sealed trait FileListState
final case class StorageState(fileMap: Map[String, UploadedFile])  extends FileListState {
  def withForm(fileId: String, form: UploadFileForm): FileListState =  copy(fileMap = fileMap + (fileId -> UploadedFile(fileId, "", Details("", form.description), Nil, Nil)))
  def withFile(newFile: UploadedFile): FileListState = copy(fileMap.updated(newFile.fileId, newFile))
}

sealed trait FileListResponse
final case class AddFile(fileId: String) extends FileListResponse
final case class GetFile(fileId: String, file: UploadedFile) extends FileListResponse


final case class UploadedFile(fileId: String, filePath: String, details: Details, streams: List[StreamDetails], outputFormats: List[Format])


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
            emptyState = StorageState(Map.empty),
            commandHandler(context, shard),
            eventHandler(context))
      context.setReceiveTimeout(30.seconds, IdleFileListCommand)
      behavior
    }
  }

  private def commandHandler(context: ActorContext[FileListCommand], shard: ActorRef[ClusterSharding.ShardCommand]): 
    (FileListState, FileListCommand) => Effect[FileListEvent, FileListState] = { (state, command) =>
    state match {
      case StorageState(fileMap) =>
        command match {
          case AddFormCommand(fileId, form, replyTo) => addForm(fileId, form, replyTo)
          case AddFileCommand(fileId, filePath, fileName, replyTo) => addFile(fileMap, fileId, filePath, fileName, replyTo)
          case GetFileCommand(fileId, replyTo) => getFile(fileMap, fileId, replyTo)
          case IdleFileListCommand => ActorSharding.passivateCluster(context, shard)
          case PassivateFileListCommand => ActorSharding.passivateActor
          case _ => Effect.unhandled
        }
      case _ => Effect.unhandled
    }
  }

  private def addForm(fileId: String, form: UploadFileForm, replyTo: ActorRef[AddFile]): Effect[FileListEvent, FileListState] = {
    val evt = AddFormEvent(fileId, form)
    Effect.persist(evt).thenRun { _ =>
      replyTo ! AddFile(fileId)
    }
  }

  private def addFile(fileMap: Map[String, UploadedFile], fileId: String, filePath: String, fileName: String, replyTo: ActorRef[GetFile]): Effect[FileListEvent, FileListState] = {
    val uploadedFile = fileMap.get(fileId).get
    val details = Details(fileName, uploadedFile.details.description)
    val path = Paths.get(filePath)
    val streams = StreamDetails.extractFrom(path)
    val outputFormats = Converter.getOutputFormats(path)
    val updatedFile = UploadedFile(fileId, filePath, details, streams, outputFormats)
    val evt = AddFileEvent(fileId, updatedFile)
    Effect.persist(evt).thenRun { _ =>
      replyTo ! GetFile(fileId, updatedFile)
    }
  }

  private def getFile(fileMap: Map[String, UploadedFile], fileId: String, replyTo: ActorRef[GetFile]): Effect[FileListEvent, FileListState] = {
    val uploadedFile = fileMap.get(fileId).get
    replyTo ! GetFile(fileId, uploadedFile)
    Effect.none
  }

  private def eventHandler(context: ActorContext[FileListCommand]): (FileListState, FileListEvent) => FileListState = { (state: FileListState, event) =>
    state match {
      case storageState: StorageState =>
        event match {
          case AddFormEvent(fileId, form) => storageState.withForm(fileId, form)
          case AddFileEvent(_, file) => storageState.withFile(file)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case _ =>
        throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

}
