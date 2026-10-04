package com.nook.app.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

data class Recording(val file: File, val durationMs: Long, val waveform: List<Float>)

/** AAC/M4A voice notes via MediaRecorder, with a live amplitude stream for the waveform. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var sampler: Job? = null
    private val samples: MutableList<Float> = java.util.Collections.synchronizedList(ArrayList())

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    val isRecording get() = recorder != null

    fun start(scope: CoroutineScope): Boolean {
        if (recorder != null) return true
        val out = File(context.cacheDir, "voice").apply { mkdirs() }.let { File(it, "vn_${System.currentTimeMillis()}.m4a") }
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = System.currentTimeMillis()
            samples.clear()
            sampler = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val norm = sqrt((amp / 32767f).coerceIn(0f, 1f))
                    _level.value = norm
                    samples += norm
                    _elapsed.value = System.currentTimeMillis() - startedAt
                    delay(80)
                }
            }
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            out.delete()
            false
        }
    }

    /** Returns null if the clip was too short or recording failed. */
    fun stop(): Recording? {
        val r = recorder ?: return null
        sampler?.cancel()
        val duration = System.currentTimeMillis() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        _level.value = 0f
        _elapsed.value = 0
        val f = file ?: return null
        if (!ok || duration < 700) { f.delete(); return null }
        return Recording(f, duration, downsample(synchronized(samples) { samples.toList() }, 48))
    }

    fun cancel() {
        val r = recorder ?: return
        sampler?.cancel()
        runCatching { r.stop() }
        r.release()
        recorder = null
        file?.delete()
        _level.value = 0f
        _elapsed.value = 0
    }

    companion object {
        fun downsample(values: List<Float>, buckets: Int): List<Float> {
            if (values.isEmpty()) return List(buckets) { 0.1f }
            val size = values.size.toFloat() / buckets
            return List(buckets) { i ->
                val from = (i * size).toInt().coerceAtMost(values.lastIndex)
                val to = ((i + 1) * size).toInt().coerceIn(from + 1, values.size)
                val v = values.subList(from, to).maxOrNull() ?: 0f
                (Math.round(v.coerceIn(0.06f, 1f) * 100) / 100f)
            }
        }
    }
}
