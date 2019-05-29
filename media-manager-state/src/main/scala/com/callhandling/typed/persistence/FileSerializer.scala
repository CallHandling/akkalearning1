package com.callhandling.typed.persistence

import java.io.NotSerializableException

import akka.actor.typed.ActorRefResolver
import akka.actor.typed.javadsl.Adapter
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import com.callhandling.typed.persistence.protobuf.FileMessage

class FileSerializer(val system: akka.actor.ExtendedActorSystem) extends SerializerWithStringManifest with BaseSerializer {

  private val resolver = ActorRefResolver(Adapter.toTyped(system))

  private val InitStateManifest = "aa"
  private val InProgressStateManifest = "ab"
  private val FinishStateManifest = "ba"
  private val UploadFileManifest = "bb"
  private val UploadInProgressCommandManifest = "bc"
  private val UploadDoneManifest = "bd"
  private val UploadedFileCommandManifest = "ca"
  private val UploadEventManifest = "cb"
  private val UploadedEventManifest = "cc"

  override def manifest(o: AnyRef): String = o match {
    case _: InitState   ⇒ InitStateManifest
    case _: InProgressState ⇒ InProgressStateManifest
    case _: FinishState     ⇒ FinishStateManifest
    case _: UploadFile ⇒ UploadFileManifest
    case _: UploadInProgressCommand  ⇒ UploadInProgressCommandManifest
    case _: UploadDone     ⇒ UploadDoneManifest
    case _: UploadedFileCommand   ⇒ UploadedFileCommandManifest
    case _: UploadEvent ⇒ UploadEventManifest
    case _: UploadedEvent   ⇒ UploadedEventManifest
    case _ ⇒
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case a: InitState   ⇒ initStateToBinary(a)
    case a: InProgressState ⇒ inProgressStateToBinary(a)
    case a: FinishState     ⇒ finishStateToBinary(a)
    case a: UploadFile ⇒ uploadFileToBinary(a)
    case a: UploadInProgressCommand  ⇒ uploadInProgressCommandToBinary(a)
    case a: UploadDone     ⇒ uploadDoneBinary(a)
    case a: UploadedFileCommand   ⇒ uploadedFileCommandToBinary(a)
    case a: UploadEvent ⇒ uploadEventToBinary(a)
    case a: UploadedEvent   ⇒ uploadedEventToBinary(a)
    case _ ⇒
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  private def initStateToBinary(a: InitState): Array[Byte] = {
    val builder = FileMessage.InitState.newBuilder()
        .setFileId(a.fileId)
    builder.build().toByteArray()
  }

  private def inProgressStateToBinary(a: InProgressState): Array[Byte] = {
    val builder = FileMessage.InProgressState.newBuilder()
    builder.setFile(uploadFileToProto(a.file)).build().toByteArray
  }

  private def finishStateToBinary(a: FinishState): Array[Byte] = {
    val builder = FileMessage.FinishState.newBuilder()
    builder.setFile(uploadFileToProto(a.file))
    builder.build().toByteArray()
  }

  private def uploadFileToBinary(a: UploadFile): Array[Byte] = {
    uploadFileToProto(a).build().toByteArray
  }

  private def uploadFileToProto(a: UploadFile): FileMessage.UploadFile.Builder  = {
    val builder = FileMessage.UploadFile.newBuilder()
    builder.setFileId(a.fileId).setByteString(a.byteString)
    builder
  }

  private def uploadInProgressCommandToBinary(a: UploadInProgressCommand): Array[Byte] = {
    val builder = FileMessage.UploadInProgressCommand.newBuilder()
    builder.setByteString(a.byteString)
    builder.setReplyTo(resolver.toSerializationFormat(a.replyTo))
    builder.build().toByteArray()
  }

  private def uploadDoneBinary(a: UploadDone): Array[Byte] = {
    val builder = FileMessage.UploadDone.newBuilder()
    builder.setFileId(a.fileId)
    builder.build().toByteArray()
  }

  private def uploadedFileCommandToBinary(a: UploadedFileCommand): Array[Byte] = {
    val builder = FileMessage.UploadedFileCommand.newBuilder()
    builder.setReplyTo(resolver.toSerializationFormat(a.replyTo))
    builder.build().toByteArray()
  }

  private def uploadEventToBinary(a: UploadEvent): Array[Byte] = {
    val builder = FileMessage.UploadEvent.newBuilder()
    builder.setFileId(a.fileId).setFile(uploadFileToProto(a.file))
    builder.build().toByteArray()
  }

  private def uploadedEventToBinary(a: UploadedEvent): Array[Byte] = {
    val builder = FileMessage.UploadedEvent.newBuilder()
    builder.setFileId(a.fileId)
    builder.build().toByteArray()
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case InitStateManifest   ⇒ initStateFromBinary(bytes)
    case InProgressStateManifest ⇒ inProgressStateFromBinary(bytes)
    case FinishStateManifest     ⇒ finishStateFromBinary(bytes)
    case UploadFileManifest ⇒ uploadFileFromBinary(bytes)
    case UploadInProgressCommandManifest  ⇒ uploadInProgressCommandFromBinary(bytes)
    case UploadDoneManifest     ⇒ publishFromBinary(bytes)
    case UploadedFileCommandManifest   ⇒ uploadedFileCommandFromBinary(bytes)
    case UploadEventManifest ⇒ uploadEventFromBinary(bytes)
    case UploadedEventManifest   ⇒ uploadedEventFromBinary(bytes)
    case _ ⇒
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  private def initStateFromBinary(bytes: Array[Byte]): InitState = {
    val a = FileMessage.InitState.parseFrom(bytes)
    InitState(a.getFileId)
  }

  private def inProgressStateFromBinary(bytes: Array[Byte]): InProgressState = {
    val a = FileMessage.InProgressState.parseFrom(bytes)
    InProgressState(UploadFile(a.getFile.getFileId, a.getFile.getByteString))
  }

  private def finishStateFromBinary(bytes: Array[Byte]): FinishState = {
    val a = FileMessage.FinishState.parseFrom(bytes)
    FinishState(UploadFile(a.getFile.getFileId, a.getFile.getByteString))
  }

  private def uploadFileFromBinary(bytes: Array[Byte]): UploadFile = {
    val a = FileMessage.UploadFile.parseFrom(bytes)
    UploadFile(a.getFileId, a.getByteString)
  }

  private def uploadInProgressCommandFromBinary(bytes: Array[Byte]): UploadInProgressCommand = {
    val a = FileMessage.UploadInProgressCommand.parseFrom(bytes)
    UploadInProgressCommand(a.getByteString, resolver.resolveActorRef(a.getReplyTo))
  }

  private def publishFromBinary(bytes: Array[Byte]): UploadDone = {
    val a = FileMessage.UploadDone.parseFrom(bytes)
    UploadDone(a.getFileId)
  }

  private def uploadedFileCommandFromBinary(bytes: Array[Byte]): UploadedFileCommand = {
    val a = FileMessage.UploadedFileCommand.parseFrom(bytes)
    UploadedFileCommand(resolver.resolveActorRef(a.getReplyTo))
  }

  private def uploadEventFromBinary(bytes: Array[Byte]): UploadEvent = {
    val a = FileMessage.UploadEvent.parseFrom(bytes)
    UploadEvent(a.getFileId, UploadFile(a.getFile.getFileId, a.getFile.getByteString))
  }

  private def uploadedEventFromBinary(bytes: Array[Byte]): UploadedEvent = {
    val a = FileMessage.UploadedEvent.parseFrom(bytes)
    UploadedEvent(a.getFileId)
  }

}
