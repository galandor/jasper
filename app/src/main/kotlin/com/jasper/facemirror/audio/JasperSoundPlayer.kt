package com.jasper.facemirror.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class JasperSoundPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastSpeakSoundMs = 0L
    private var lastPeakAmplitude = 0f

    /** Радостный звук при появлении человека. */
    fun playGreeting() {
        scope.launch {
            playTone(880, 120)
            delay(90)
            playTone(1108, 120)
            delay(90)
            playTone(1318, 120)
            delay(90)
            playTone(1760, 180)
        }
    }

    /** Короткий «голосовой» звук при речи — срабатывает на пиках громкости. */
    fun onSpeechAmplitude(amplitude: Float) {
        val now = System.currentTimeMillis()
        val rising = amplitude > lastPeakAmplitude + 0.06f
        lastPeakAmplitude = amplitude

        if (!rising || amplitude < 0.18f) return
        if (now - lastSpeakSoundMs < MIN_SPEAK_INTERVAL_MS) return
        lastSpeakSoundMs = now

        val baseFreq = 180f + amplitude * 220f
        scope.launch {
            playVowel(baseFreq, 70 + (amplitude * 60).toInt())
        }
    }

    fun release() {
        // звуки короткие, завершаются сами
    }

    private fun playTone(frequency: Int, durationMs: Int) {
        playVowel(frequency.toFloat(), durationMs)
    }

    /** Синтез простого гласного — несколько синусоид. */
    private fun playVowel(baseHz: Float, durationMs: Int) {
        val sampleRate = 22_050
        val count = sampleRate * durationMs / 1000
        val buffer = ShortArray(count)

        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val envelope = envelopeAt(i, count)
            val sample = (
                sin(2 * PI * baseHz * t) * 0.55 +
                    sin(2 * PI * baseHz * 2.02f * t) * 0.25 +
                    sin(2 * PI * baseHz * 3.1f * t) * 0.12
                ) * envelope
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        scope.launch {
            delay(durationMs.toLong() + 30)
            track.stop()
            track.release()
        }
    }

    private fun envelopeAt(index: Int, total: Int): Float {
        val attack = total * 0.08f
        val release = total * 0.25f
        return when {
            index < attack -> index / attack
            index > total - release -> (total - index) / release
            else -> 1f
        }
    }

    companion object {
        private const val MIN_SPEAK_INTERVAL_MS = 110L
    }
}
