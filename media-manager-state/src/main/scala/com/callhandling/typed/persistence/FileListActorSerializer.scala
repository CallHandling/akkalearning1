package com.callhandling.typed.persistence

import java.io.NotSerializableException
/*
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import com.callhandling.typed.persistence.protobuf.{FileListActorProto}


class FileListActorSerializer(val system: akka.actor.ExtendedActorSystem) extends SerializerWithStringManifest with BaseSerializer {

  private val fileActorSerializer = new FileActorSerializer(system)

  private val AddEventManifest = "aa"
  private val GetEventManifest = "ab"

  override def manifest(o: AnyRef): String = o match {
    case _: AddEvent ⇒ AddEventManifest
    case _: GetEvent ⇒ GetEventManifest
    case _ ⇒
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case a: AddEvent ⇒ addEventToBinary(a)
    case a: GetEvent ⇒ getEventToBinary(a)
    case _ ⇒
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  private def addEventToBinary(a: AddEvent): Array[Byte] = {
    val builder = FileListActorProto.AddEvent.newBuilder()
    builder.setFileId(a.fileId).setFile(fileActorSerializer.uploadedFileToProto(a.file).build())
    builder.build().toByteArray()
  }

  private def getEventToBinary(a: GetEvent): Array[Byte] = {
    val builder = FileListActorProto.AddEvent.newBuilder()
    builder.setFileId(a.fileId)
    builder.build().toByteArray()
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case AddEventManifest   ⇒ addEventFromBinary(bytes)
    case GetEventManifest   ⇒ getEventFromBinary(bytes)
    case _ ⇒
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  private def addEventFromBinary(bytes: Array[Byte]): AddEvent = {
    val a = FileListActorProto.AddEvent.parseFrom(bytes)
    AddEvent(a.getFileId, fileActorSerializer.uploadedFile(a.getFile))
  }

  private def getEventFromBinary(bytes: Array[Byte]): GetEvent = {
    val a = FileListActorProto.GetEvent.parseFrom(bytes)
    GetEvent(a.getFileId)
  }
}
*/