package com.app.fluenscene

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Generates SRT subtitles for local videos that don't already have an
 * embedded text track, using Groq's hosted Whisper transcription API -
 * the same Groq account/API key FluenScene already asks users to connect.
 *
 * Pipeline:
 *  1. The video's audio track is copied out in short chunks directly from
 *     the local file (MediaExtractor -> MediaMuxer, no re-encoding), since
 *     Groq's transcription endpoint caps uploads at 25MB (free tier) /
 *     100MB (dev tier) - too small for a whole movie's audio in one call.
 *  2. Each chunk (a small standalone .m4a file) is uploaded to
 *     https://api.groq.com/openai/v1/audio/transcriptions with
 *     response_format=verbose_json, which returns per-segment start/end/text.
 *  3. Segment timestamps are shifted by the chunk's real start time and
 *     written out as one combined .srt file.
 *  4. The .srt is cached in app-private storage, keyed by the video's URI,
 *     so a given video is only ever transcribed once.
 *
 * Uses INTERNET permission you already have declared (same as Firebase/Groq
 * calls elsewhere), so no manifest change is needed.
 *
 * Known limitation: extraction only re-containers the existing audio track,
 * it doesn't transcode it. That works for the large majority of MP4/MKV
 * files (AAC/MP3 audio), but a source track in a codec with no on-device
 * decoder (e.g. AC3/DTS on some devices) can fail to extract - generate()
 * simply returns null for that video in that case.
 */
object AutoSubtitleGenerator {

    // Keep chunks comfortably under Groq's 25MB free-tier upload limit.
    // Safe to raise (e.g. 20-25 min) if the connected key is on Groq's dev tier (100MB).
    private const val CHUNK_DURATION_MS = 6 * 60 * 1000L
    private const val TRANSCRIPTION_MODEL = "whisper-large-v3-turbo"

    /** Where a generated subtitle file for this video would live, whether or not it exists yet. */
    fun getCachedSubtitleFile(context: Context, videoUri: Uri): File {
        val dir = File(context.filesDir, "auto_subtitles").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(videoUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.srt")
    }

    /**
     * Runs the full pipeline and returns the finished .srt file, or null if
     * nothing could be transcribed. Safe to call repeatedly - returns the
     * cached result instantly on later calls for the same video.
     *
     * [onProgress] fires on a background thread before each chunk is sent;
     * that's fine to write straight into Compose State from, no need to hop
     * threads yourself.
     */
    suspend fun generate(
        context: Context,
        videoUri: Uri,
        apiKey: String,
        videoDurationMs: Long,
        onProgress: (chunkIndex: Int, totalChunks: Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val outputFile = getCachedSubtitleFile(context, videoUri)
        if (outputFile.exists() && outputFile.length() > 0) return@withContext outputFile

        val totalChunks = ((videoDurationMs + CHUNK_DURATION_MS - 1) / CHUNK_DURATION_MS)
            .toInt().coerceAtLeast(1)

        val srtBuilder = StringBuilder()
        var entryIndex = 1
        val tempDir = File(context.cacheDir, "subtitle_chunks").apply { mkdirs() }

        try {
            for (chunk in 0 until totalChunks) {
                onProgress(chunk + 1, totalChunks)

                val startMs = chunk * CHUNK_DURATION_MS
                val endMs = ((chunk + 1) * CHUNK_DURATION_MS).coerceAtMost(videoDurationMs)
                val chunkFile = File(tempDir, "chunk_$chunk.m4a")

                val actualOffsetMs = extractAudioChunk(context, videoUri, startMs, endMs, chunkFile)
                if (actualOffsetMs == null || chunkFile.length() == 0L) {
                    chunkFile.delete()
                    continue
                }

                val segments = try {
                    transcribeChunk(chunkFile, apiKey)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                } finally {
                    chunkFile.delete()
                }

                val offsetSec = actualOffsetMs / 1000.0
                for (seg in segments) {
                    val text = seg.text.trim()
                    if (text.isEmpty()) continue
                    srtBuilder.append(entryIndex).append('\n')
                    srtBuilder.append(formatSrtTime(seg.startSec + offsetSec))
                        .append(" --> ")
                        .append(formatSrtTime(seg.endSec + offsetSec))
                        .append('\n')
                    srtBuilder.append(text).append("\n\n")
                    entryIndex++
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }

        if (entryIndex == 1) return@withContext null // nothing transcribed successfully

        outputFile.writeText(srtBuilder.toString())
        outputFile
    }

    /**
     * Copies just the audio samples between [startMs] and [endMs] out of
     * [videoUri] into [outputFile] as a small standalone .m4a file. Returns
     * the real timestamp (ms) of the first copied sample, or null on failure.
     */
    private fun extractAudioChunk(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        outputFile: File
    ): Long? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var wroteAnySample = false

        try {
            extractor.setDataSource(context, videoUri, null)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var firstSampleTimeUs = -1L

            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs == -1L || sampleTimeUs > endMs * 1000) break

                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                if (firstSampleTimeUs == -1L) firstSampleTimeUs = sampleTimeUs

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTimeUs - firstSampleTimeUs
                bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                wroteAnySample = true
                extractor.advance()
            }

            return if (wroteAnySample) firstSampleTimeUs / 1000 else null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { muxer?.stop() } catch (e: Exception) {}
            try { muxer?.release() } catch (e: Exception) {}
            extractor.release()
        }
    }

    private data class TranscriptSegment(val startSec: Double, val endSec: Double, val text: String)

    private suspend fun transcribeChunk(audioFile: File, apiKey: String): List<TranscriptSegment> =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", TRANSCRIPTION_MODEL)
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("language", "en")
                .addFormDataPart(
                    "file", audioFile.name,
                    audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string()
            if (!response.isSuccessful || bodyStr == null) {
                return@withContext emptyList()
            }

            val json = JSONObject(bodyStr)
            val segmentsArray = json.optJSONArray("segments") ?: return@withContext emptyList()
            (0 until segmentsArray.length()).mapNotNull { i ->
                val seg = segmentsArray.optJSONObject(i) ?: return@mapNotNull null
                TranscriptSegment(
                    startSec = seg.optDouble("start", 0.0),
                    endSec = seg.optDouble("end", 0.0),
                    text = seg.optString("text", "")
                )
            }
        }

    private fun formatSrtTime(totalSeconds: Double): String {
        val ms = (totalSeconds * 1000).toLong().coerceAtLeast(0)
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val millis = ms % 1000
        // ADD java.util.Locale.US so periods and commas are strictly formatted
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", h, m, s, millis)
    }}