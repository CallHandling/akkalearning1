package com.callhandling.typed.persistence


import java.nio.file.Paths

import akka.Done
import akka.actor.typed.javadsl.Adapter
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey, EventSourcedEntity}
import akka.cluster.typed.{Cluster, Join}
import akka.persistence.typed.scaladsl.Effect
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.util.{ByteString, Timeout}
import com.callhandling.Forms.{ConvertFileForm, UploadFileForm}
import com.callhandling.actors.FileActor.Details
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.MediaStream
import com.callhandling.typed.cluster.ActorSharding
import com.typesafe.config.Config

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.{Success, Try}
import akka.stream.alpakka.s3.BucketAccess.{AccessDenied, AccessGranted, NotExists}
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3
import com.callhandling.media.converters.Progress
import scala.util.Success
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.IOResult
import com.callhandling.media.converters.{Progress, Formats}
import com.callhandling.typed.persistence.FileConvertResponse.ConvertStatus

sealed trait FilePipeline
case object FilePipeline {
  final case object FileHD extends FilePipeline {
    def getPath(fileId: String) = Paths.get(s"/tmp/${fileId}")
  }
  final case object AmazonS3 extends FilePipeline {
    val bucketName = "hubbub-ph-training"
  }
}

/*
sealed trait FileListCommand
final case object IdleFileListCommand extends FileListCommand
final case object PassivateFileListCommand extends FileListCommand
final case class AddFormCommand(fileId: String, form: UploadFileForm, replyTo: ActorRef[AddFile]) extends FileListCommand
final case class AddFileCommand(filePipeline: FilePipeline, fileId: String, fileSource: Source[ByteString, Any], fileName: String, replyTo: ActorRef[GetFile]) extends FileListCommand
final case class GetFileCommand(filePipeline: FilePipeline, fileId: String, replyTo: ActorRef[GetFile]) extends FileListCommand
final case class ConvertFileCommand(filePipeline: FilePipeline, convertFileForm: ConvertFileForm, replyTo: ActorRef[AddFile]) extends FileListCommand

sealed trait FileListEvent
final case class AddFormEvent(fileId: String, form: UploadFileForm) extends FileListEvent
final case class AddFileEvent(fileId: String, file: UploadedFile) extends FileListEvent
final case class GetFileEvent(fileId: String) extends FileListEvent
final case class ConvertFileEvent(fileId: String) extends FileListEvent

sealed trait FileListState
final case class StorageState(fileMap: Map[String, UploadedFile])  extends FileListState {
  def withForm(fileId: String, form: UploadFileForm): FileListState =
    copy(fileMap = fileMap + (fileId -> UploadedFile(fileId, Details("", form.description), Vector(), Vector())))
  def withFile(newFile: UploadedFile): FileListState = copy(fileMap.updated(newFile.fileId, newFile))
  def withS3(context: ActorContext[FileListCommand]): FileListState = {
    implicit val materializerTyped = ActorMaterializer()(context.system)
    implicit val executionContextTyped = context.executionContext
    S3.checkIfBucketExists(FilePipeline.AmazonS3.bucketName).map {
      case NotExists => context.log.info(s"${FilePipeline.AmazonS3.bucketName} : NotExists")
      case AccessGranted => context.log.info(s"${FilePipeline.AmazonS3.bucketName} : AccessGranted")
      case AccessDenied => context.log.info(s"${FilePipeline.AmazonS3.bucketName} : AccessDenied")
    }
    this
  }
}

sealed trait FileListResponse
final case class AddFile(fileId: String) extends FileListResponse
final case class GetFile(fileId: String, file: UploadedFile, fileSource: Source[ByteString, Any]) extends FileListResponse


final case class UploadedFile(fileId: String, details: Details, streams: Vector[MediaStream], outputFormats: Vector[Format])


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
            emptyState = StorageState(Map.empty).withS3(context),
            commandHandler(context, shard),
            eventHandler(context))
      context.setReceiveTimeout(30.seconds, IdleFileListCommand)
      behavior
    }
  }

  private def commandHandler(context: ActorContext[FileListCommand], shard: ActorRef[ClusterSharding.ShardCommand]):
    (FileListState, FileListCommand) => Effect[FileListEvent, FileListState] = { (state, command) =>
    implicit val materializer = akka.stream.ActorMaterializer.create(Adapter.toUntyped(context.system))
    implicit val materializerTyped = ActorMaterializer()(context.system)
    implicit val executionContextTyped = context.executionContext
    state match {
      case StorageState(fileMap) =>
        command match {
          case AddFormCommand(fileId, form, replyTo) => addForm(fileId, form, replyTo)
          case AddFileCommand(filePipeline, fileId, fileSource, fileName, replyTo) => addFile(materializer, executionContextTyped, filePipeline, fileMap, fileId, fileSource, fileName, replyTo)
          case GetFileCommand(filePipeline, fileId, replyTo) => getFile(materializerTyped, executionContextTyped, filePipeline, fileMap, fileId, replyTo)
          case ConvertFileCommand(filePipeline, convertFileForm, replyTo) => convertFile(context, shard, materializerTyped, executionContextTyped, filePipeline, fileMap, convertFileForm, replyTo)
//          case IdleFileListCommand => ActorSharding.passivateCluster(context, shard)
//          case PassivateFileListCommand => ActorSharding.passivateActor
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

  private def addFile(implicit mat: akka.stream.ActorMaterializer, executionContext: ExecutionContextExecutor, filePipeline: FilePipeline, fileMap: Map[String,
      UploadedFile], fileId: String, fileSource: Source[ByteString, Any], fileName: String,
      replyTo: ActorRef[GetFile]): Effect[FileListEvent, FileListState] = {
    filePipeline match {
      case file @ FilePipeline.FileHD => {
        val filePath = file.getPath(fileId)
        val sink = FileIO.toPath(filePath)
        val writeResult = Await.result(fileSource.runWith(sink), 10.seconds)
        writeResult.status match {
          case Success(_) => {
            val uploadedFile = fileMap.get(fileId).get
            val details = Details(fileName, uploadedFile.details.description)
            val streams = MediaStream.extractFrom(filePath.toAbsolutePath.toString)
            val outputFormats = Formats.outputFormatsOf(filePath)
            val updatedFile = UploadedFile(fileId, details, streams, outputFormats)
            val evt = AddFileEvent(fileId, updatedFile)
            Effect.persist(evt).thenRun { _ =>
              replyTo ! GetFile(fileId, updatedFile, FileIO.fromPath(filePath))
            }
          }
        }
      }
      case file @ FilePipeline.AmazonS3 => {
        val sink = S3.multipartUpload(file.bucketName, fileId);
        val writeResult: MultipartUploadResult = Await.result(fileSource.runWith(sink), 5.seconds)
        val uploadedFile = fileMap.get(fileId).get
        val details = Details(fileName, uploadedFile.details.description)
        val streams = StreamDetails.extractFrom(writeResult.location.path.toString())
        val outputFormats = Converter.getOutputFormats(writeResult.location.path.toString())
        val updatedFile = UploadedFile(fileId, details, streams, outputFormats)
        val evt = AddFileEvent(fileId, updatedFile)
        Effect.persist(evt).thenRun { _ =>
          replyTo ! GetFile(fileId, updatedFile, Await.result(S3.download(file.bucketName, fileId).runWith(Sink.head), 5.seconds).get._1)
        }
      }
    }
  }

  private def getFile(implicit actorMaterializer: ActorMaterializer, executionContext: ExecutionContextExecutor, filePipeline: FilePipeline, fileMap: Map[String, UploadedFile], fileId: String, replyTo: ActorRef[GetFile]): Effect[FileListEvent, FileListState] = {
    filePipeline match {
      case file @ FilePipeline.FileHD => {
        val uploadedFile = fileMap.get(fileId).get
        replyTo ! GetFile(fileId, uploadedFile, FileIO.fromPath(file.getPath(fileId)))
        Effect.none
      }
    }
  }

  private def convertFile(context: ActorContext[FileListCommand], shard: ActorRef[ClusterSharding.ShardCommand], actorMaterializer: ActorMaterializer, executionContext: ExecutionContextExecutor, filePipeline: FilePipeline, fileMap: Map[String, UploadedFile], convertFileForm: ConvertFileForm, replyTo: ActorRef[AddFile]): Effect[FileListEvent, FileListState] = {
    implicit val systemTyped = context.system
    //  implicit val materializerTyped = akka.stream.typed.scaladsl.ActorMaterializer()(systemTyped)
    implicit val executionContextTyped: ExecutionContextExecutor = systemTyped.executionContext
    implicit val schedulerTyped = systemTyped.scheduler
    implicit val timeout: Timeout = 3.seconds

    filePipeline match {
      case file @ FilePipeline.FileHD => {
        val id = ActorSharding.generateEntityId;
        val fileConvertActorSharding = ActorSharding(FileConvertActor, 3)
        val fileConvertActorEntityRef = fileConvertActorSharding.entityRefFor(FileConvertActor.entityTypeKey, ActorSharding.generateEntityId)
        val fileSource = FileIO.fromPath(file.getPath(convertFileForm.fileId))
        fileConvertActorEntityRef ! FileConvertCommand.Start(fileSource, convertFileForm)

        val future: Future[ConvertStatus] = fileConvertActorEntityRef.ask(ref => FileConvertCommand.Status(ref))
        val convertStatus = Await.result(future, 3.seconds)
        context.log.info("conversionStatus: "+ convertStatus.fileId +" : "+ convertStatus.percentComplete)

        replyTo ! AddFile(id)
        Effect.none
      }
    }
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
*/