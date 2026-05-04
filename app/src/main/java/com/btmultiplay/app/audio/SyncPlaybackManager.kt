package com.btmultiplay.app.audio

import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import com.btmultiplay.app.bluetooth.BtDeviceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin

enum class PlaybackMode {
    SINGLE,          // One speaker at a time
    DUAL_AUDIO,      // Device supports true simultaneous BT output
    SYNCHRONIZED     // Software sync via duplicate AudioTrack routing
}

data class SyncStatus(
    val mode: PlaybackMode,
    val activeDeviceAddresses: List<String>,
    val isPlaying: Boolean,
    val latencyWarning: Boolean = false
)

class SyncPlaybackManager(
    private val context: Context,
    private val audioRoutingManager: AudioRoutingManager
) {
    private val TAG = "SyncPlaybackManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _syncStatus = MutableStateFlow(
        SyncStatus(PlaybackMode.SINGLE, emptyList(), false)
    )
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // Demo tone generator for testing sync
    private var demoJob: Job? = null
    private val audioTracks = mutableListOf<AudioTrack>()

    fun buildSyncPlan(connectedDevices: List<BtDeviceInfo>): PlaybackMode {
        val capability = audioRoutingManager.capability.value
        return when {
            connectedDevices.size <= 1 -> PlaybackMode.SINGLE
            capability.supportsDualAudio -> PlaybackMode.DUAL_AUDIO
            else -> PlaybackMode.SYNCHRONIZED
        }
    }

    /**
     * Play a synchronized test tone across all connected BT outputs.
     * This demonstrates sync by playing the same PCM data to multiple AudioTracks,
     * each routed to a different audio output device.
     */
    fun startSyncTestTone(connectedDevices: List<BtDeviceInfo>) {
        stopPlayback()
        val mode = buildSyncPlan(connectedDevices)
        val outputs = audioRoutingManager.getConnectedBluetoothOutputs()

        _syncStatus.value = SyncStatus(
            mode = mode,
            activeDeviceAddresses = connectedDevices.map { it.address },
            isPlaying = true,
            latencyWarning = mode == PlaybackMode.SYNCHRONIZED
        )

        demoJob = scope.launch {
            if (outputs.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                playOnDefaultOutput()
            } else {
                when (mode) {
                    PlaybackMode.SINGLE -> playOnDefaultOutput()
                    PlaybackMode.DUAL_AUDIO -> playDualAudio(outputs)
                    PlaybackMode.SYNCHRONIZED -> playSynchronized(outputs)
                }
            }
        }
    }

    private suspend fun playOnDefaultOutput() {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTracks.add(track)
        track.play()

        val tone = generateTone(440.0, sampleRate, 3)
        track.write(tone, 0, tone.size)
        delay(3000)
        track.stop()
        track.release()
        audioTracks.remove(track)

        _syncStatus.value = _syncStatus.value.copy(isPlaying = false)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private suspend fun playDualAudio(outputs: List<AudioDeviceInfo>) {
        // Route to up to 2 outputs simultaneously
        val targetOutputs = outputs.take(2)
        val jobs = targetOutputs.map { output ->
            scope.async {
                playToOutput(output, 440.0)
            }
        }
        jobs.awaitAll()
        _syncStatus.value = _syncStatus.value.copy(isPlaying = false)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private suspend fun playSynchronized(outputs: List<AudioDeviceInfo>) {
        // Create one AudioTrack per output, start them as close together as possible
        val sampleRate = 44100
        val tracks = outputs.map { output ->
            createTrackForOutput(output, sampleRate)
        }
        audioTracks.addAll(tracks)

        val tone = generateTone(440.0, sampleRate, 3)

        // Prime all tracks with initial buffer
        tracks.forEach { it.write(tone, 0, minOf(tone.size / 4, it.bufferSizeInFrames * 2)) }

        // Start all simultaneously
        tracks.forEach { it.play() }

        // Feed remaining data
        var offset = minOf(tone.size / 4, tracks.firstOrNull()?.bufferSizeInFrames?.times(2) ?: 0)
        while (offset < tone.size && isActive) {
            val chunkSize = minOf(2048, tone.size - offset)
            tracks.forEach { it.write(tone, offset, chunkSize) }
            offset += chunkSize
        }

        delay(3200)
        tracks.forEach { try { it.stop(); it.release() } catch (e: Exception) { } }
        audioTracks.removeAll(tracks.toSet())
        _syncStatus.value = _syncStatus.value.copy(isPlaying = false)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private suspend fun playToOutput(output: AudioDeviceInfo, freq: Double) {
        val sampleRate = 44100
        val track = createTrackForOutput(output, sampleRate)
        audioTracks.add(track)
        track.play()
        val tone = generateTone(freq, sampleRate, 3)
        track.write(tone, 0, tone.size)
        delay(3200)
        try { track.stop(); track.release() } catch (e: Exception) { }
        audioTracks.remove(track)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun createTrackForOutput(output: AudioDeviceInfo, sampleRate: Int): AudioTrack {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            track.preferredDevice = output
        }
        return track
    }

    private fun generateTone(freqHz: Double, sampleRate: Int, durationSec: Int): ShortArray {
        val numSamples = durationSec * sampleRate
        val samples = ShortArray(numSamples * 2) // stereo
        val fadeLen = sampleRate / 10 // 100ms fade

        for (i in 0 until numSamples) {
            val angle = 2 * PI * i * freqHz / sampleRate
            var amplitude = 0.3 * Short.MAX_VALUE

            // Fade in
            if (i < fadeLen) amplitude *= i.toDouble() / fadeLen
            // Fade out
            if (i > numSamples - fadeLen) amplitude *= (numSamples - i).toDouble() / fadeLen

            val sample = (sin(angle) * amplitude).toInt().toShort()
            samples[i * 2] = sample
            samples[i * 2 + 1] = sample
        }
        return samples
    }

    fun stopPlayback() {
        demoJob?.cancel()
        audioTracks.forEach { try { it.stop(); it.release() } catch (e: Exception) { } }
        audioTracks.clear()
        _syncStatus.value = _syncStatus.value.copy(isPlaying = false)
    }

    fun destroy() {
        stopPlayback()
        scope.cancel()
    }
}
