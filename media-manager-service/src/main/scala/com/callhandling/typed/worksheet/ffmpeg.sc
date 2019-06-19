import java.nio.file.{Files, Paths}

import com.callhandling.media.FFmpegConf
import com.github.kokorin.jaffree.LogLevel
import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, PipeInput, PipeOutput}

FFmpeg.atPath(FFmpegConf.Bin)
  .addInput(PipeInput.pumpFrom(Files.newInputStream(Paths.get("/home/ryzen/Videos/test.mp4")))
    .addArguments("-f", "mp3")
  )
  .addOutput(PipeOutput.pumpTo(Files.newOutputStream(Paths.get("/home/ryzen/Videos/test.mp3")))
    .addArguments("-ar", "11025")
    .addArguments("-ac", "1")
    .addArguments("-c:a", "libmp3lame")
    .addArguments("-f", "mp3")
  )
  .setLogLevel(LogLevel.DEBUG)
  .execute()


//def convert(fromFilename: String, toFilename: String, format: String, channels: Int, sampleRate: Int,  codec: String) = {
//  FFmpeg.atPath(FFmpegConf.Bin)
//    .addInput(PipeInput.pumpFrom(Files.newInputStream(Paths.get(s"/home/ryzen/Music/$fromFilename"))))
//    .addOutput(PipeOutput.pumpTo(Files.newOutputStream(Paths.get(s"/home/ryzen/Music/$toFilename")))
//      .addArguments("-ar", sampleRate.toString)
//      .addArguments("-ac", channels.toString)
//      .addArguments("-c:a", codec)
//      .addArguments("-f", format)
//    )
//    .setLogLevel(LogLevel.DEBUG)
//    .execute()
//}
//
//convert("test1.mp3", "testConverted1.mp3", "mp3", 1, 11025, "libmp3lame")
//convert("test1.mp3", "testConverted2.wav", "wav", 1, 8000, "pcm_s16le")
//convert("test1.mp3", "testConverted3.wav", "wav", 1, 11500, "pcm_s16le")
//convert("test1.mp3", "testConverted4.wav", "wav", 1, 22000, "pcm_s16le")
//convert("test1.mp3", "testConverted5.wav", "wav", 1, 44100, "pcm_s16le")
//convert("test1.mp3", "testConverted6.wav", "wav", 1, 48000, "pcm_s16le")