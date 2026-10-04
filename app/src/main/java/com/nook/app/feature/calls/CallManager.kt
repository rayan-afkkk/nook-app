package com.nook.app.feature.calls

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nook.app.AppConfig
import com.nook.app.data.model.CallStatus
import com.nook.app.data.model.CallType
import com.nook.app.data.remote.WorkerApi
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.CallRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.notifications.Notifier
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CallPhase { Ringing, Connecting, Connected, Ended }

data class ActiveCall(
    val callId: String?,
    val peerUid: String,
    val peerName: String,
    val peerPhoto: String?,
    val type: CallType,
    val outgoing: Boolean,
    val phase: CallPhase,
    val connectedAt: Long = 0,
    val micOn: Boolean = true,
    val speakerOn: Boolean = false,
    val cameraOn: Boolean = false,
    val frontCamera: Boolean = true,
    val remoteVideo: VideoTrack? = null,
    val localVideo: VideoTrack? = null,
    val message: String? = null,
)

/**
 * One call at a time. Signalling lives in Firestore (calls/{id}); push ringing goes through the
 * Worker (high-priority FCM → full-screen intent); media goes through LiveKit.
 */
class CallManager(
    private val context: Context,
    private val calls: CallRepository,
    private val users: UserRepository,
    private val worker: WorkerApi,
    private val auth: AuthRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<ActiveCall?>(null)
    val state: StateFlow<ActiveCall?> = _state.asStateFlow()

    private var room: Room? = null
    private var jobs = mutableListOf<Job>()

    fun startCall(chatId: String, peerUid: String, type: CallType) {
        if (_state.value != null) return
        scope.launch {
            val peer = runCatching { users.getUser(peerUid) }.getOrNull()
            _state.value = ActiveCall(null, peerUid, peer?.name ?: "Friend", peer?.photoUrl, type, outgoing = true, phase = CallPhase.Ringing, cameraOn = type == CallType.VIDEO)
            if (!AppConfig.hasLiveKit || !AppConfig.hasWorker) {
                endWithMessage("Calls aren't set up yet (LiveKit + Worker keys missing).")
                return@launch
            }
            try {
                val callId = calls.create(chatId, peerUid, type)
                _state.update { it?.copy(callId = callId) }
                watchCall(callId)
                launch { runCatching { worker.ringCall(callId) } }
                startService()
                connect(callId, type)
                // No answer in 45 s → missed.
                jobs += launch {
                    delay(45_000)
                    if (_state.value?.phase == CallPhase.Ringing) {
                        runCatching { calls.setStatus(callId, CallStatus.MISSED) }
                        runCatching { worker.cancelCall(callId) }
                        endWithMessage("No answer")
                    }
                }
            } catch (e: Exception) {
                endWithMessage("Couldn't start the call")
            }
        }
    }

    /** From the notification / incoming screen. */
    fun answer(callId: String) {
        if (_state.value?.callId == callId) return
        Notifier.cancelIncomingCall(context, callId)
        scope.launch {
            val call = runCatching { calls.get(callId) }.getOrNull() ?: return@launch
            if (call.status != CallStatus.RINGING) return@launch
            val peerUid = call.callerId
            val peer = runCatching { users.getUser(peerUid) }.getOrNull()
            _state.value = ActiveCall(callId, peerUid, peer?.name ?: "Friend", peer?.photoUrl, call.type, outgoing = false, phase = CallPhase.Connecting, cameraOn = call.type == CallType.VIDEO)
            runCatching { calls.setStatus(callId, CallStatus.ACCEPTED) }
            watchCall(callId)
            startService()
            connect(callId, call.type)
        }
    }

    fun decline(callId: String) {
        Notifier.cancelIncomingCall(context, callId)
        scope.launch { runCatching { calls.setStatus(callId, CallStatus.DECLINED) } }
    }

    fun hangUp() {
        val s = _state.value ?: return
        val id = s.callId
        scope.launch {
            if (id != null) {
                if (s.phase == CallPhase.Ringing && s.outgoing) {
                    runCatching { calls.setStatus(id, CallStatus.MISSED) }
                    runCatching { worker.cancelCall(id) }
                } else {
                    runCatching { calls.setStatus(id, CallStatus.ENDED) }
                }
            }
            endWithMessage("Call ended")
        }
    }

    private fun watchCall(callId: String) {
        jobs += scope.launch {
            calls.observe(callId).filterNotNull().collect { call ->
                when (call.status) {
                    CallStatus.ACCEPTED -> _state.update { cur ->
                        if (cur != null && cur.phase == CallPhase.Ringing) cur.copy(phase = CallPhase.Connecting) else cur
                    }
                    CallStatus.DECLINED -> endWithMessage("Declined")
                    CallStatus.ENDED -> if (_state.value?.phase != CallPhase.Ended) endWithMessage("Call ended")
                    CallStatus.MISSED -> if (_state.value?.outgoing == false) endWithMessage("Missed call")
                    CallStatus.RINGING -> Unit
                }
            }
        }
    }

    private suspend fun connect(callId: String, type: CallType) {
        try {
            val token = worker.liveKitToken(callId)
            val r = LiveKit.create(appContext = context.applicationContext)
            room = r
            jobs += scope.launch {
                r.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed -> (event.track as? VideoTrack)?.let { v -> _state.update { it?.copy(remoteVideo = v) } }
                        is RoomEvent.TrackUnsubscribed -> if (event.track is VideoTrack) _state.update { it?.copy(remoteVideo = null) }
                        is RoomEvent.ParticipantConnected -> markConnected()
                        is RoomEvent.ParticipantDisconnected -> endWithMessage("Call ended")
                        is RoomEvent.Disconnected -> if (_state.value?.phase != CallPhase.Ended) endWithMessage("Disconnected")
                        else -> Unit
                    }
                }
            }
            r.connect(AppConfig.liveKitUrl, token)
            r.localParticipant.setMicrophoneEnabled(_state.value?.micOn ?: true)
            if (type == CallType.VIDEO) {
                r.localParticipant.setCameraEnabled(true)
                refreshLocalVideo()
                setSpeaker(true)
            }
            if (r.remoteParticipants.isNotEmpty()) markConnected()
        } catch (e: Exception) {
            endWithMessage("Couldn't connect the call")
        }
    }

    private fun markConnected() {
        _state.update { cur ->
            if (cur == null || cur.phase == CallPhase.Connected) cur
            else cur.copy(phase = CallPhase.Connected, connectedAt = System.currentTimeMillis())
        }
    }

    private fun refreshLocalVideo() {
        val track = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
        _state.update { it?.copy(localVideo = track) }
    }

    /** Video views must be initialised against the room's EGL context before tracks attach. */
    fun initRenderer(view: io.livekit.android.renderer.TextureViewRenderer) {
        room?.initVideoRenderer(view)
    }

    fun toggleMic() {
        val on = !(_state.value?.micOn ?: true)
        _state.update { it?.copy(micOn = on) }
        scope.launch { runCatching { room?.localParticipant?.setMicrophoneEnabled(on) } }
    }

    fun toggleCamera() {
        val on = !(_state.value?.cameraOn ?: false)
        _state.update { it?.copy(cameraOn = on) }
        scope.launch {
            runCatching { room?.localParticipant?.setCameraEnabled(on) }
            if (on) refreshLocalVideo() else _state.update { it?.copy(localVideo = null) }
        }
    }

    fun flipCamera() {
        val front = !(_state.value?.frontCamera ?: true)
        (room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack)
            ?.switchCamera(position = if (front) CameraPosition.FRONT else CameraPosition.BACK)
        _state.update { it?.copy(frontCamera = front) }
    }

    fun toggleSpeaker() = setSpeaker(!(_state.value?.speakerOn ?: false))

    private fun setSpeaker(on: Boolean) {
        val handler = room?.audioSwitchHandler
        if (handler != null) {
            val device = handler.availableAudioDevices.firstOrNull {
                if (on) it is AudioDevice.Speakerphone else it !is AudioDevice.Speakerphone
            }
            handler.selectDevice(device)
        }
        _state.update { it?.copy(speakerOn = on) }
    }

    private fun endWithMessage(msg: String) {
        if (_state.value?.phase == CallPhase.Ended) return
        _state.update { it?.copy(phase = CallPhase.Ended, message = msg, remoteVideo = null, localVideo = null) }
        jobs.forEach { it.cancel() }
        jobs = mutableListOf()
        room?.let { r -> runCatching { r.disconnect() }; runCatching { r.release() } }
        room = null
        stopService()
        scope.launch {
            delay(1200)
            if (_state.value?.phase == CallPhase.Ended) _state.value = null
        }
    }

    private fun startService() {
        val micGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!micGranted) return
        runCatching { ContextCompat.startForegroundService(context, Intent(context, CallService::class.java)) }
    }

    private fun stopService() {
        runCatching { context.stopService(Intent(context, CallService::class.java)) }
    }
}
