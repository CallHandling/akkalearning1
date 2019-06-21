package com.callhandling.typed.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey, EventSourcedEntity}
import akka.cluster.typed.{Cluster, Join}
import akka.persistence.typed.scaladsl.Effect
import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.util.ByteString
import com.callhandling.web.Forms.ConvertFileForm
import com.callhandling.actors.FileActor.Details
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.MediaStream
import com.callhandling.typed.cluster.ActorSharding
import com.callhandling.typed.persistence.FileConvertResponse.{ConvertFile, ConvertStatus}
import com.typesafe.config.Config

import scala.concurrent.duration._

sealed trait FileConvertCommand
final case object FileConvertCommand {
  final case object Idle extends FileConvertCommand
  final case object Passivate extends FileConvertCommand
  final case class Start(fileSource: Source[ByteString, Any], form: ConvertFileForm) extends FileConvertCommand
  final case class Status(replyTo: ActorRef[ConvertStatus]) extends FileConvertCommand
}

sealed trait FileConvertEvent
final case object FileConvertEvent {
  final case class Start(fileId: String, form: ConvertFileForm) extends FileConvertEvent
  final case class InProgress(fileId: String, form: ConvertFileForm, percentComplete: Float) extends FileConvertEvent
  final case class Completed(fileId: String) extends FileConvertEvent
}

sealed trait FileConvertState
final case object FileConvertState {
  final case class Init(fileId: String) extends FileConvertState
  final case class InProgress(fileId: String, form: ConvertFileForm, percentComplete: Float) extends FileConvertState {
    def start(materializer: ActorMaterializer): FileConvertState = {
      val filePath = FilePipeline.FileHD.getPath(fileId)
      /*Converter.convert(materializer, FileIO.fromPath(filePath), 42625.9375f, form) { progress =>
        println(progress.percent)
        copy(percentComplete = progress.percent)
      }
      if(percentComplete == 100) Complete(fileId, form) else*/ this
    }
  }
  final case class Complete(fileId: String, form: ConvertFileForm) extends FileConvertState
}

sealed trait FileConvertResponse
final case object FileConvertResponse {
  final case class ConvertFile(fileId: String) extends FileConvertResponse
  final case class ConvertedFile(fileId: String, file: UploadedFile, fileSource: Source[ByteString, Any]) extends FileConvertResponse
  final case class ConvertStatus(fileId: String, percentComplete: Float) extends FileConvertResponse
}

object FileConvertActor extends ActorSharding[FileConvertCommand] {
  import FileConvertCommand._
  import FileConvertEvent._
  import FileConvertState._
  import FileConvertResponse._

  val entityTypeKey: EntityTypeKey[FileConvertCommand] = EntityTypeKey[FileConvertCommand]("FileConvertActor")

  override def shardingCluster(clusterName: String, config: Config): ClusterSharding = {
    val system = ActorSystem(Behaviors.empty[FileConvertCommand], clusterName, config)
    val sharding = ClusterSharding(system)
    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(
      Entity(
        typeKey = entityTypeKey,
        createBehavior = entityContext => shardingBehavior(entityContext.shard, entityContext.entityId))
        .withStopMessage(Passivate))
    sharding
  }

  override def shardingBehavior(shard: ActorRef[ClusterSharding.ShardCommand], entityId: String): Behavior[FileConvertCommand] = {
    Behaviors.setup { context =>
      def behavior: Behavior[FileConvertCommand] =
          EventSourcedEntity[FileConvertCommand, FileConvertEvent, FileConvertState](
            entityTypeKey = entityTypeKey,
            entityId = entityId,
            emptyState = Init(entityId),
            commandHandler(context, shard),
            eventHandler(context))
      context.setReceiveTimeout(30.seconds, Idle)
      behavior
    }
  }

  private def commandHandler(context: ActorContext[FileConvertCommand], shard: ActorRef[ClusterSharding.ShardCommand]):
    (FileConvertState, FileConvertCommand) => Effect[FileConvertEvent, FileConvertState] = { (state, command) =>
    implicit val materializerTyped = ActorMaterializer()(context.system)
    implicit val executionContextTyped = context.executionContext
    state match {
      case FileConvertState.Init(fileId) =>
        command match {
          case FileConvertCommand.Start(fileSource, form) => startConvert(materializerTyped, fileSource, fileId, form)
          case _ => Effect.unhandled
        }
      case FileConvertState.InProgress(fileId, _, percentComplete) =>
        command match {
          case FileConvertCommand.Status(replyTo) => getStatus(fileId, percentComplete, replyTo)
//          case Idle => ActorSharding.passivateCluster(context, shard)
//          case Passivate => ActorSharding.passivateActor
          case _ => Effect.unhandled
        }
      case _ => Effect.unhandled
    }
  }

  private def getStatus(fileId: String, percentComplete: Float, replyTo: ActorRef[ConvertStatus]): Effect[FileConvertEvent, FileConvertState] = {
    replyTo ! ConvertStatus(fileId, percentComplete)
    Effect.none
  }

  private def startConvert(implicit materializer: ActorMaterializer, fileSource: Source[ByteString, Any], fileId: String, form: ConvertFileForm): Effect[FileConvertEvent, FileConvertState] = {
    val evt = FileConvertEvent.Start(fileId, form)
    Effect.persist(evt)
  }

  private def eventHandler(context: ActorContext[FileConvertCommand]): (FileConvertState, FileConvertEvent) => FileConvertState = { (state: FileConvertState, event) =>
    implicit val materializerTyped = ActorMaterializer()(context.system)
    implicit val executionContextTyped = context.executionContext
    state match {
      case FileConvertState.Init(_) =>
        event match {
          case FileConvertEvent.Start(fileId, form) => FileConvertState.InProgress(fileId, form, 0.0f).start(materializerTyped)
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case state @ FileConvertState.InProgress(_, _, _) =>
        event match {
          case FileConvertEvent.InProgress(fileId, form, percentComplete) => {
            if(percentComplete == 100) FileConvertState.Complete(fileId, form)
            else state.copy(percentComplete = percentComplete)
          }
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case _ =>
        throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

}
