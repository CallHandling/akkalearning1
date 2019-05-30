package com.callhandling.typed.persistence

import java.io.NotSerializableException

import akka.actor.typed.ActorRefResolver
import akka.actor.typed.javadsl.Adapter
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import com.callhandling.typed.persistence.protobuf.FileActorMessage

class FileActorSerializer(val system: akka.actor.ExtendedActorSystem) extends SerializerWithStringManifest with BaseSerializer {

  private val resolver = ActorRefResolver(Adapter.toTyped(system))

  private val InitStateManifest = "aa"
  private val InProgressStateManifest = "ab"
  private val FinishStateManifest = "ba"
  private val UploadFileManifest = "bb"
  private val UploadInProgressCommandManifest = "bc"
  private val UploadedFileManifest = "bd"
  private val UploadedFileCommandManifest = "ca"
  private val UploadEventManifest = "cb"
  private val UploadedEventManifest = "cc"
  private val MultimediaFileInfoManifest = "cd"
  private val AudioFileInfoManifest = "da"
  private val VideoFileInfoManifest = "db"
  private val VideoDimensionManifest = "dc"

  override def manifest(o: AnyRef): String = o match {
    case _: InitState   ⇒ InitStateManifest
    case _: InProgressState ⇒ InProgressStateManifest
    case _: FinishState     ⇒ FinishStateManifest
    case _: UploadFile ⇒ UploadFileManifest
    case _: UploadInProgressCommand  ⇒ UploadInProgressCommandManifest
    case _: UploadedFile     ⇒ UploadedFileManifest
    case _: UploadedFileCommand   ⇒ UploadedFileCommandManifest
    case _: UploadEvent ⇒ UploadEventManifest
    case _: UploadedEvent   ⇒ UploadedEventManifest
    case _: MultimediaFileInfo   ⇒ MultimediaFileInfoManifest
    case _: AudioFileInfo   ⇒ AudioFileInfoManifest
    case _: VideoFileInfo   ⇒ VideoFileInfoManifest
    case _: VideoDimension   ⇒ VideoDimensionManifest
    case _ ⇒
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case a: InitState   ⇒ initStateToBinary(a)
    case a: InProgressState ⇒ inProgressStateToBinary(a)
    case a: FinishState     ⇒ finishStateToBinary(a)
    case a: UploadFile ⇒ uploadFileToBinary(a)
    case a: UploadInProgressCommand  ⇒ uploadInProgressCommandToBinary(a)
    case a: UploadedFile     ⇒ uploadedFileBinary(a)
    case a: UploadedFileCommand   ⇒ uploadedFileCommandToBinary(a)
    case a: UploadEvent ⇒ uploadEventToBinary(a)
    case a: UploadedEvent   ⇒ uploadedEventToBinary(a)
    case a: MultimediaFileInfo   ⇒ multimediaFileInfoToBinary(a)
    case a: AudioFileInfo   ⇒ audioFileInfoToBinary(a)
    case a: VideoFileInfo   ⇒ videoFileInfoToBinary(a)
    case a: VideoDimension   ⇒ videoDimensionToBinary(a)
    case _ ⇒
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  private def multimediaFileInfoToBinary(a: MultimediaFileInfo): Array[Byte] = {
    multimediaFileInfoToProto(a).build().toByteArray
  }

  private def multimediaFileInfoToProto(a: MultimediaFileInfo): FileActorMessage.MultimediaFileInfo.Builder  = {
    val builder = FileActorMessage.MultimediaFileInfo.newBuilder()
    builder.setFormat(a.format).setDuration(a.duration)
    a.audio.map(o => builder.setAudio(audioFileInfoToProto(o)))
    a.video.map(o => builder.setVideo(videoFileInfoToProto(o)))
    builder
  }

  private def audioFileInfoToBinary(a: AudioFileInfo): Array[Byte] = {
    audioFileInfoToProto(a).build().toByteArray
  }

  private def audioFileInfoToProto(a: AudioFileInfo): FileActorMessage.AudioFileInfo.Builder  = {
    val builder = FileActorMessage.AudioFileInfo.newBuilder()
    builder.setDecoder(a.decoder).setSamplingRate(a.samplingRate)
    builder.setChannels(a.channels).setBitRate(a.bitRate)
    builder
  }

  private def videoFileInfoToBinary(a: VideoFileInfo): Array[Byte] = {
    videoFileInfoToProto(a).build().toByteArray
  }

  private def videoFileInfoToProto(a: VideoFileInfo): FileActorMessage.VideoFileInfo.Builder  = {
    val builder = FileActorMessage.VideoFileInfo.newBuilder()
    builder.setDecoder(a.decoder)
    a.dimension.map(d => builder.setDimension(videoDimensionToProto(d)))
    builder.setBitRate(a.bitRate).setFrameRate(a.frameRate)
    builder
  }

  private def videoDimensionToBinary(a: VideoDimension): Array[Byte] = {
    videoDimensionToProto(a).build().toByteArray
  }

  private def videoDimensionToProto(a: VideoDimension): FileActorMessage.VideoDimension.Builder  = {
    val builder = FileActorMessage.VideoDimension.newBuilder()
    builder.setWidth(a.width).setHeight(a.height)
    builder
  }

  private def initStateToBinary(a: InitState): Array[Byte] = {
    val builder = FileActorMessage.InitState.newBuilder()
        .setFileId(a.fileId)
    builder.build().toByteArray()
  }

  private def inProgressStateToBinary(a: InProgressState): Array[Byte] = {
    val builder = FileActorMessage.InProgressState.newBuilder()
    builder.setFile(uploadFileToProto(a.file)).build().toByteArray
  }

  private def finishStateToBinary(a: FinishState): Array[Byte] = {
    val builder = FileActorMessage.FinishState.newBuilder()
    builder.setFile(uploadFileToProto(a.file))
    builder.build().toByteArray()
  }

  private def uploadFileToBinary(a: UploadFile): Array[Byte] = {
    uploadFileToProto(a).build().toByteArray
  }

  private def uploadFileToProto(a: UploadFile): FileActorMessage.UploadFile.Builder  = {
    val builder = FileActorMessage.UploadFile.newBuilder()
    builder.setFileId(a.fileId).setByteString(a.byteString)
    builder
  }

  private def uploadInProgressCommandToBinary(a: UploadInProgressCommand): Array[Byte] = {
    val builder = FileActorMessage.UploadInProgressCommand.newBuilder()
    builder.setByteString(a.byteString)
    builder.setReplyTo(resolver.toSerializationFormat(a.replyTo))
    builder.build().toByteArray()
  }

  private def uploadedFileBinary(a: UploadedFile): Array[Byte] = {
    val builder = FileActorMessage.UploadedFile.newBuilder()
    builder.setFileId(a.fileId).setByteString(a.byteString)
    a.multimediaFileInfo.map(o => builder.setFileInfo(multimediaFileInfoToProto(o)))
    builder.build().toByteArray()
  }

  private def uploadedFileCommandToBinary(a: UploadedFileCommand): Array[Byte] = {
    val builder = FileActorMessage.UploadedFileCommand.newBuilder()
    builder.setReplyTo(resolver.toSerializationFormat(a.replyTo))
    builder.build().toByteArray()
  }

  private def uploadEventToBinary(a: UploadEvent): Array[Byte] = {
    val builder = FileActorMessage.UploadEvent.newBuilder()
    builder.setFileId(a.fileId).setFile(uploadFileToProto(a.file))
    builder.build().toByteArray()
  }

  private def uploadedEventToBinary(a: UploadedEvent): Array[Byte] = {
    val builder = FileActorMessage.UploadedEvent.newBuilder()
    builder.setFileId(a.fileId)
    builder.build().toByteArray()
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case InitStateManifest   ⇒ initStateFromBinary(bytes)
    case InProgressStateManifest ⇒ inProgressStateFromBinary(bytes)
    case FinishStateManifest     ⇒ finishStateFromBinary(bytes)
    case UploadFileManifest ⇒ uploadFileFromBinary(bytes)
    case UploadInProgressCommandManifest  ⇒ uploadInProgressCommandFromBinary(bytes)
    case UploadedFileManifest     ⇒ uploadedFileFromBinary(bytes)
    case UploadedFileCommandManifest   ⇒ uploadedFileCommandFromBinary(bytes)
    case UploadEventManifest ⇒ uploadEventFromBinary(bytes)
    case UploadedEventManifest   ⇒ uploadedEventFromBinary(bytes)
    case _ ⇒
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  private def initStateFromBinary(bytes: Array[Byte]): InitState = {
    val a = FileActorMessage.InitState.parseFrom(bytes)
    InitState(a.getFileId)
  }

  private def inProgressStateFromBinary(bytes: Array[Byte]): InProgressState = {
    val a = FileActorMessage.InProgressState.parseFrom(bytes)
    InProgressState(UploadFile(a.getFile.getFileId, a.getFile.getByteString))
  }

  private def finishStateFromBinary(bytes: Array[Byte]): FinishState = {
    val a = FileActorMessage.FinishState.parseFrom(bytes)
    FinishState(UploadFile(a.getFile.getFileId, a.getFile.getByteString))
  }

  private def uploadFileFromBinary(bytes: Array[Byte]): UploadFile = {
    val a = FileActorMessage.UploadFile.parseFrom(bytes)
    UploadFile(a.getFileId, a.getByteString)
  }

  private def uploadInProgressCommandFromBinary(bytes: Array[Byte]): UploadInProgressCommand = {
    val a = FileActorMessage.UploadInProgressCommand.parseFrom(bytes)
    UploadInProgressCommand(a.getByteString, resolver.resolveActorRef(a.getReplyTo))
  }

  private def getVideoDimension(a: FileActorMessage.VideoDimension): Option[VideoDimension] = {
    Some(VideoDimension(a.getWidth, a.getHeight))
  }

  private def getVideoFileInfo(a: FileActorMessage.VideoFileInfo): Option[VideoFileInfo] = {
    val dimension = if(a.hasDimension) {
      getVideoDimension(a.getDimension)
    } else None
    Some(VideoFileInfo(a.getDecoder, dimension, a.getBitRate, a.getFrameRate))
  }

  private def getAudioFileInfo(a: FileActorMessage.AudioFileInfo): Option[AudioFileInfo] = {
    Some(AudioFileInfo(a.getDecoder, a.getSamplingRate, a.getChannels, a.getBitRate))
  }

  private def getMultimediaFileInfo(a: FileActorMessage.MultimediaFileInfo): Option[MultimediaFileInfo] = {
    val audio = if(a.hasAudio) {
      getAudioFileInfo(a.getAudio)
    } else None
    val video = if(a.hasVideo) {
      getVideoFileInfo(a.getVideo)
    } else None
    Some(MultimediaFileInfo(a.getFormat, a.getDuration, audio, video))
  }

  private def uploadedFileFromBinary(bytes: Array[Byte]): UploadedFile = {
    val a = FileActorMessage.UploadedFile.parseFrom(bytes)
    val fileInfo = if(a.hasFileInfo) {
      getMultimediaFileInfo(a.getFileInfo)
    } else None
    UploadedFile(a.getFileId, a.getByteString, fileInfo)
  }

  private def uploadedFileCommandFromBinary(bytes: Array[Byte]): UploadedFileCommand = {
    val a = FileActorMessage.UploadedFileCommand.parseFrom(bytes)
    UploadedFileCommand(resolver.resolveActorRef(a.getReplyTo))
  }

  private def uploadEventFromBinary(bytes: Array[Byte]): UploadEvent = {
    val a = FileActorMessage.UploadEvent.parseFrom(bytes)
    UploadEvent(a.getFileId, UploadFile(a.getFile.getFileId, a.getFile.getByteString))
  }

  private def uploadedEventFromBinary(bytes: Array[Byte]): UploadedEvent = {
    val a = FileActorMessage.UploadedEvent.parseFrom(bytes)
    UploadedEvent(a.getFileId)
  }

}
