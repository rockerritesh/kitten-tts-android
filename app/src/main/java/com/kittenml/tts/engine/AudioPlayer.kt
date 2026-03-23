package com.kittenml.tts.engine

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioPlayer {
    companion object {
        const val SAMPLE_RATE = 24000

        fun saveAsWav(context: Context, samples: FloatArray, fileName: String): String? {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.wav")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeWav(out, samples)
            }

            return "$fileName.wav"
        }

        private fun writeWav(out: OutputStream, samples: FloatArray) {
            // Convert float32 to int16 PCM
            val pcmData = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                val clamped = sample.coerceIn(-1.0f, 1.0f)
                pcmData.putShort((clamped * 32767).toInt().toShort())
            }
            val pcmBytes = pcmData.array()

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            val dataSize = pcmBytes.size
            val fileSize = 36 + dataSize

            // RIFF header
            header.put("RIFF".toByteArray())
            header.putInt(fileSize)
            header.put("WAVE".toByteArray())
            // fmt chunk
            header.put("fmt ".toByteArray())
            header.putInt(16)          // chunk size
            header.putShort(1)         // PCM format
            header.putShort(1)         // mono
            header.putInt(SAMPLE_RATE) // sample rate
            header.putInt(SAMPLE_RATE * 2) // byte rate (sampleRate * channels * bitsPerSample/8)
            header.putShort(2)         // block align
            header.putShort(16)        // bits per sample
            // data chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            out.write(header.array())
            out.write(pcmBytes)
        }
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
