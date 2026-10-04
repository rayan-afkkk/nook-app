package com.nook.app.data.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(val messageId: String? = null, val playing: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0)

/** One shared Media3 player for voice notes; starting one pauses the other. */
class VoicePlayer(private val context: Context) {
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private fun ensure(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { p ->
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(playing = isPlaying)
                if (isPlaying) startTicker() else ticker?.cancel()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _state.value = _state.value.copy(playing = false, positionMs = 0)
                    p.seekTo(0); p.pause()
                }
                if (playbackState == Player.STATE_READY) _state.value = _state.value.copy(durationMs = p.duration.coerceAtLeast(0))
            }
        })
        player = p
    }

    fun toggle(messageId: String, url: String) {
        val p = ensure()
        if (_state.value.messageId == messageId) {
            if (p.isPlaying) p.pause() else p.play()
            return
        }
        _state.value = PlaybackState(messageId = messageId)
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.play()
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        if (p.duration > 0) p.seekTo((p.duration * fraction).toLong())
    }

    fun stop() {
        player?.stop()
        _state.value = PlaybackState()
    }

    fun release() {
        ticker?.cancel()
        player?.release()
        player = null
        _state.value = PlaybackState()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                player?.let { _state.value = _state.value.copy(positionMs = it.currentPosition, durationMs = it.duration.coerceAtLeast(0)) }
                delay(50)
            }
        }
    }
}
