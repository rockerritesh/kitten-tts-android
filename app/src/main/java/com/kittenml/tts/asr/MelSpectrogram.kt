package com.kittenml.tts.asr

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Computes the 80-bin log-mel spectrogram that Whisper's encoder expects.
 *
 * Matches OpenAI Whisper's `log_mel_spectrogram`:
 *  - 16 kHz mono input, padded/trimmed to 30 s (480000 samples)
 *  - n_fft = 400, hop = 160, Hann window, reflect padding (center=true)
 *  - 80 Slaney mel filters over [0, 8000] Hz → 3000 frames
 *  - log10, dynamic-range clamp to 8 dB, scaled to roughly [-1, 1]
 *
 * Output is a flat FloatArray of shape [80 * 3000] in row-major
 * (mel, frame) order — ready to wrap as an ONNX tensor [1, 80, 3000].
 */
object MelSpectrogram {

    const val SAMPLE_RATE = 16000
    const val N_FFT = 400
    const val HOP = 160
    const val N_MELS = 80
    const val CHUNK_SECONDS = 30
    const val N_SAMPLES = SAMPLE_RATE * CHUNK_SECONDS      // 480000
    const val N_FRAMES = N_SAMPLES / HOP                   // 3000
    private const val N_BINS = N_FFT / 2 + 1               // 201
    private const val PAD = N_FFT / 2                       // 200

    private val hann: FloatArray by lazy {
        FloatArray(N_FFT) { 0.5f * (1f - cos(2.0 * PI * it / N_FFT).toFloat()) }
    }

    // Precomputed DFT basis for the 201 real-FFT bins: cosTab[bin][n], sinTab[bin][n].
    private val cosTab: Array<FloatArray> by lazy { dftTable(useCos = true) }
    private val sinTab: Array<FloatArray> by lazy { dftTable(useCos = false) }

    private fun dftTable(useCos: Boolean): Array<FloatArray> = Array(N_BINS) { bin ->
        FloatArray(N_FFT) { n ->
            val angle = -2.0 * PI * bin * n / N_FFT
            if (useCos) cos(angle).toFloat() else sin(angle).toFloat()
        }
    }

    private val melFilters: Array<FloatArray> by lazy { buildMelFilters() }

    /**
     * @param audio mono 16 kHz float PCM. Padded with zeros or trimmed to 30 s.
     * @return flat [80 * 3000] log-mel features.
     */
    fun compute(audio: FloatArray): FloatArray {
        // Pad/trim to exactly 30 s, then reflect-pad by N_FFT/2 for centered frames.
        val signal = FloatArray(N_SAMPLES + 2 * PAD)
        val copyLen = min(audio.size, N_SAMPLES)
        System.arraycopy(audio, 0, signal, PAD, copyLen)
        // Reflect pad (matches torch.stft center=true). Guard against short clips.
        for (k in 0 until PAD) {
            val src = PAD + min(k + 1, N_SAMPLES - 1).coerceAtLeast(0)
            if (src < signal.size) signal[PAD - 1 - k] = signal[src]
            val rightSrc = PAD + N_SAMPLES - 2 - k
            if (rightSrc in signal.indices) signal[PAD + N_SAMPLES + k] = signal[rightSrc]
        }

        // power[frame][bin]
        val mel = FloatArray(N_MELS * N_FRAMES)
        val frame = FloatArray(N_FFT)
        val power = FloatArray(N_BINS)
        var maxLog = Float.NEGATIVE_INFINITY

        // Temp store all log-mel then normalize; track max for the 8 dB clamp.
        for (t in 0 until N_FRAMES) {
            val start = t * HOP
            for (n in 0 until N_FFT) frame[n] = signal[start + n] * hann[n]

            for (b in 0 until N_BINS) {
                val cb = cosTab[b]
                val sb = sinTab[b]
                var re = 0f
                var im = 0f
                for (n in 0 until N_FFT) {
                    val s = frame[n]
                    re += s * cb[n]
                    im += s * sb[n]
                }
                power[b] = re * re + im * im
            }

            for (mi in 0 until N_MELS) {
                val filt = melFilters[mi]
                var sum = 0f
                for (b in 0 until N_BINS) sum += filt[b] * power[b]
                val logVal = log10(max(sum, 1e-10f))
                mel[mi * N_FRAMES + t] = logVal
                if (logVal > maxLog) maxLog = logVal
            }
        }

        // Whisper normalization: clamp to (max - 8), then (x + 4) / 4.
        val floor = maxLog - 8f
        for (idx in mel.indices) {
            var v = mel[idx]
            if (v < floor) v = floor
            mel[idx] = (v + 4f) / 4f
        }
        return mel
    }

    // ── Slaney mel filterbank (librosa default, htk=false), as used by Whisper ──

    private fun hzToMel(hz: Float): Float {
        val fSp = 200f / 3f
        val minLogHz = 1000f
        val minLogMel = minLogHz / fSp
        val logStep = (ln(6.4f) / 27f)
        return if (hz < minLogHz) hz / fSp
        else minLogMel + ln(hz / minLogHz) / logStep
    }

    private fun melToHz(mel: Float): Float {
        val fSp = 200f / 3f
        val minLogHz = 1000f
        val minLogMel = minLogHz / fSp
        val logStep = (ln(6.4f) / 27f)
        return if (mel < minLogMel) fSp * mel
        else minLogHz * Math.exp((logStep * (mel - minLogMel)).toDouble()).toFloat()
    }

    private fun buildMelFilters(): Array<FloatArray> {
        val fMin = 0f
        val fMax = SAMPLE_RATE / 2f // 8000
        val fftFreqs = FloatArray(N_BINS) { it * SAMPLE_RATE.toFloat() / N_FFT }

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = FloatArray(N_MELS + 2) { melMin + (melMax - melMin) * it / (N_MELS + 1) }
        val hzPoints = FloatArray(N_MELS + 2) { melToHz(melPoints[it]) }

        return Array(N_MELS) { m ->
            val left = hzPoints[m]
            val center = hzPoints[m + 1]
            val right = hzPoints[m + 2]
            val enorm = 2f / (right - left)
            FloatArray(N_BINS) { k ->
                val f = fftFreqs[k]
                val lower = (f - left) / (center - left)
                val upper = (right - f) / (right - center)
                max(0f, min(lower, upper)) * enorm
            }
        }
    }
}
