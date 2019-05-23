package com.callhandling.typed.ffprobe

case class FFProbeMetadata (
                           streams: Seq[Streams],
                           format: Format
                         )

case class Disposition (
                         default: Int,
                         dub: Int,
                         original: Int,
                         comment: Int,
                         lyrics: Int,
                         karaoke: Int,
                         forced: Int,
                         hearing_impaired: Int,
                         visual_impaired: Int,
                         clean_effects: Int,
                         attached_pic: Int
                       )

case class Format (
                    filename: String,
                    nb_streams: Int,
                    nb_programs: Int,
                    format_name: String,
                    format_long_name: String,
                    start_time: String,
                    duration: String,
                    size: String,
                    bit_rate: String,
                    probe_score: Int,
                    tags: FormatTag
                  )

case class Streams (
                     index: Int,
                     codec_name: String,
                     codec_long_name: String,
                     profile: String,
                     codec_type: String,
                     codec_time_base: String,
                     codec_tag_string: String,
                     codec_tag: String,
                     width: Option[Int],
                     height: Option[Int],
                     coded_width: Option[Int],
                     coded_height: Option[Int],
                     has_b_frames: Option[Int],
                     sample_aspect_ratio: Option[String],
                     display_aspect_ratio: Option[String],
                     pix_fmt: Option[String],
                     level: Option[Int],
                     chroma_location: Option[String],
                     refs: Option[Int],
                     is_avc: Option[String],
                     nal_length_size: Option[String],
                     r_frame_rate: String,
                     avg_frame_rate: String,
                     time_base: String,
                     start_pts: Int,
                     start_time: String,
                     duration_ts: Int,
                     duration: String,
                     bit_rate: String,
                     bits_per_raw_sample: Option[String],
                     nb_frames: String,
                     disposition: Disposition,
                     tags: StreamTag,
                     sample_fmt: Option[String],
                     sample_rate: Option[String],
                     channels: Option[Int],
                     channel_layout: Option[String],
                     bits_per_sample: Option[Int],
                     max_bit_rate: Option[String]
                   )

case class StreamTag (
                       language: String,
                       handler_name: String
                     )

case class FormatTag (
                       major_brand: String,
                       minor_version: String,
                       compatible_brands: String,
                       encoder: String
                     )
