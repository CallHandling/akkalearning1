package com.callhandling.typed.persistence

import java.nio.file.{Files, Paths}

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey, EventSourcedEntity}
import akka.cluster.typed.{Cluster, Join}
import akka.persistence.typed.scaladsl.Effect
import com.callhandling.typed.cluster.ActorSharding
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import ws.schild.jave.{AudioInfo, MultimediaInfo, MultimediaObject, VideoInfo, VideoSize}

import scala.concurrent.duration._
//import FileActorMessage._
//import FileActorMessage.Command._
//import FileActorMessage.Event._
//import FileActorMessage.State._
//import FileActorMessage.Response._

sealed trait FileCommand
final case object IdleFileCommand extends FileCommand
final case object PassivateFileCommand extends FileCommand
final case class UploadInProgressCommand(byteString: ByteString, replyTo: ActorRef[UploadFile]) extends FileCommand
final case class UploadedFileCommand(replyTo: ActorRef[UploadedFile]) extends FileCommand

sealed trait FileEvent
final case class UploadEvent(fileId: String, file: UploadFile) extends FileEvent
final case class UploadedEvent(fileId: String) extends FileEvent

sealed trait FileState
final case class InitState(fileId: String) extends FileState
final case class InProgressState(file: UploadFile)  extends FileState {
  def withFile(newFile: UploadFile): FileState = copy(file = UploadFile(file.fileId, file.byteString.concat(newFile.byteString)))
  def fileId: String = file.fileId
}
final case class FinishState(file: UploadFile) extends FileState {
  def fileId: String = file.fileId
}

sealed trait FileResponse
final case class UploadFile(fileId: String, byteString: ByteString) extends FileResponse
final case class UploadedFile(fileId: String, byteString: ByteString, multimediaFileInfo: Option[MultimediaFileInfo]) extends FileResponse

final case class MultimediaFileInfo(format: String, duration: Long, audio: Option[AudioFileInfo], video: Option[VideoFileInfo])
object MultimediaFileInfo {
  def apply(multimediaInfo: Option[MultimediaInfo]): Option[MultimediaFileInfo] = multimediaInfo.map(m => MultimediaFileInfo(m.getFormat, m.getDuration,
    AudioFileInfo(Option(m.getAudio)), VideoFileInfo(Option(m.getVideo))))
}
final case class AudioFileInfo(decoder: String, samplingRate: Int, channels: Int, bitRate: Int)
object AudioFileInfo {
  def apply(audio: Option[AudioInfo]): Option[AudioFileInfo] = audio.map(a => AudioFileInfo(a.getDecoder, a.getSamplingRate, a.getChannels, a.getBitRate))
}
final case class VideoFileInfo(decoder: String, dimension: Option[VideoDimension], bitRate: Int, frameRate: Float)
object VideoFileInfo {
  def apply(video: Option[VideoInfo]): Option[VideoFileInfo] = video.map(v => VideoFileInfo(v.getDecoder, VideoDimension(Option(v.getSize)), v.getBitRate, v.getFrameRate))

}
final case class VideoDimension(width: Int, height: Int)
object VideoDimension {
  def apply(size: Option[VideoSize]): Option[VideoDimension] = size.map(s => VideoDimension(s.getWidth, s.getHeight))
}


object FileActor extends ActorSharding[FileCommand] {

  val entityTypeKey: EntityTypeKey[FileCommand] = EntityTypeKey[FileCommand]("FileActor")
  val MaxNumberOfShards = 1000

  override def shardingCluster(clusterName: String, config: Config): ClusterSharding = {
    val system = ActorSystem(Behaviors.empty[FileCommand], clusterName, config)
    val sharding = ClusterSharding(system)
    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(
      Entity(
        typeKey = entityTypeKey,
        createBehavior = entityContext => shardingBehavior(entityContext.shard, entityContext.entityId))
        .withStopMessage(PassivateFileCommand))
    sharding
  }

  override def shardingBehavior(shard: ActorRef[ClusterSharding.ShardCommand], entityId: String): Behavior[FileCommand] = {
    Behaviors.setup { context =>
      def behavior: Behavior[FileCommand] =
          EventSourcedEntity[FileCommand, FileEvent, FileState](
            entityTypeKey = entityTypeKey,
            entityId = entityId,
            emptyState = InitState(entityId),
            commandHandler(context, shard),
            eventHandler(context))
      context.setReceiveTimeout(30.seconds, IdleFileCommand)
      behavior
    }
  }

  private def commandHandler(context: ActorContext[FileCommand], shard: ActorRef[ClusterSharding.ShardCommand]): (FileState, FileCommand) => Effect[FileEvent, FileState] = { (state, command) =>
    state match {
      case InitState(fileId) =>
        command match {
          case UploadInProgressCommand(byteString, replyTo) => uploadFile(UploadFile(fileId, byteString), replyTo)
          case IdleFileCommand => ActorSharding.passivateCluster(context, shard)
          case PassivateFileCommand => ActorSharding.passivateActor
          case _ => Effect.unhandled
        }
      case inProgressState @ InProgressState(file) =>
        command match {
          case UploadInProgressCommand(byteString, replyTo) => uploadFile(UploadFile(file.fileId, byteString), replyTo)
          case UploadedFileCommand(replyTo) => uploadedFile(inProgressState, replyTo)
          case IdleFileCommand => ActorSharding.passivateCluster(context, shard)
          case PassivateFileCommand => ActorSharding.passivateActor
          case _ => Effect.unhandled
        }
      case FinishState(_) =>
        command match {
          case IdleFileCommand => ActorSharding.passivateCluster(context, shard)
          case PassivateFileCommand => ActorSharding.passivateActor
          case _ => Effect.unhandled
        }
      case _ => Effect.unhandled
    }
  }

  private def uploadFile(file: UploadFile, replyTo: ActorRef[UploadFile]): Effect[FileEvent, FileState] = {
    val evt = UploadEvent(file.fileId, file)
    Effect.persist(evt).thenRun { _ =>
      replyTo ! file
    }
  }

  private def uploadedFile(state: InProgressState, replyTo: ActorRef[UploadedFile]): Effect[FileEvent, FileState] = {
    val evt = UploadedEvent(state.fileId)
    Effect.persist(evt).thenRun { _ =>
      val tmp = Paths.get("/tmp/" + state.file.fileId)
      Files.write(tmp, state.file.byteString.toByteArray)
      val info = Option(new MultimediaObject(tmp.toFile).getInfo)
      Files.delete(tmp)

      replyTo ! UploadedFile(state.file.fileId, state.file.byteString, MultimediaFileInfo(info))
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
          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case _ =>
        throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

}
