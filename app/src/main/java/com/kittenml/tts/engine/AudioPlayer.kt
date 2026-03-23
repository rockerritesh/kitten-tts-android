package com.kittenml.tts.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioPlayer {
    companion object {
        const val SAMPLE_RATE = 24000
    }

    private var audioTrack: AudioTrack? = null

    fun play(samples: FloatArray) {
        stop()

        val bufferSize = samples.size * Float.SIZE_BYTES
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        audioTrack = track
    }

    fun stop() {
        audioTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        }
        audioTrack = null
    }
}
