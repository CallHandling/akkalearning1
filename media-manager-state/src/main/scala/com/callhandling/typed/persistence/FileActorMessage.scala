//package com.callhandling.typed.persistence
//
//import akka.actor.typed.ActorRef
//import com.google.protobuf.ByteString
//import ws.schild.jave.{AudioInfo, MultimediaInfo, VideoInfo, VideoSize}
//
//object FileActorMessage {
//
////  import Response._
//
//  sealed trait FileCommand
////  case object Command extends Serializable {
//    final case object IdleCommand extends FileCommand
//    final case object PassivateCommand extends FileCommand
//    final case class UploadInProgressCommand(byteString: ByteString, replyTo: ActorRef[UploadFile]) extends FileCommand
//    final case class UploadedFileCommand(replyTo: ActorRef[UploadedFile]) extends FileCommand
////  }
//
//  sealed trait FileEvent
////  case object Event extends Serializable {
//    final case class UploadEvent(fileId: String, file: UploadFile) extends FileEvent
//    final case class UploadedEvent(fileId: String) extends FileEvent
////  }
//
//  sealed trait FileState
////  case object State extends Serializable {
//    final case class InitState(fileId: String) extends FileState
//    final case class InProgressState(file: UploadFile) extends FileState {
//      def withFile(newFile: UploadFile): FileState = copy(file = UploadFile(file.fileId, file.byteString.concat(newFile.byteString)))
//      def fileId: String = file.fileId
//    }
//    final case class FinishState(file: UploadFile) extends FileState {
//      def fileId: String = file.fileId
//    }
////  }
//
//  sealed trait FileResponse
////  case object Response {
//    final case class UploadFile(fileId: String, byteString: ByteString) extends FileResponse
//    final case class UploadedFile(fileId: String, byteString: ByteString, multimediaFileInfo: Option[MultimediaFileInfo]) extends FileResponse
//    final case class MultimediaFileInfo(format: String, duration: Long, audio: Option[AudioFileInfo], video: Option[VideoFileInfo])
//
//    case object MultimediaFileInfo {
//      def apply(multimediaInfo: Option[MultimediaInfo]): Option[MultimediaFileInfo] = multimediaInfo.map(m => MultimediaFileInfo(m.getFormat, m.getDuration,
//        AudioFileInfo(Option(m.getAudio)), VideoFileInfo(Option(m.getVideo))))
//    }
//
//    final case class AudioFileInfo(decoder: String, samplingRate: Int, channels: Int, bitRate: Int)
//    case object AudioFileInfo {
//      def apply(audio: Option[AudioInfo]): Option[AudioFileInfo] = audio.map(a => AudioFileInfo(a.getDecoder, a.getSamplingRate, a.getChannels, a.getBitRate))
//    }
//
//    final case class VideoFileInfo(decoder: String, dimension: Option[VideoDimension], bitRate: Int, frameRate: Float)
//    case object VideoFileInfo {
//      def apply(video: Option[VideoInfo]): Option[VideoFileInfo] = video.map(v => VideoFileInfo(v.getDecoder, VideoDimension(Option(v.getSize)), v.getBitRate, v.getFrameRate))
//    }
//
//    final case class VideoDimension(width: Int, height: Int)
//    case object VideoDimension {
//      def apply(size: Option[VideoSize]): Option[VideoDimension] = size.map(s => VideoDimension(s.getWidth, s.getHeight))
//    }
////  }
//
//}
