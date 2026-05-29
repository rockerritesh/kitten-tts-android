package com.kittenml.tts.ui.screen.practice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kittenml.tts.asr.AsrEngineProvider
import com.kittenml.tts.asr.AsrState
import com.kittenml.tts.asr.AudioRecorder
import com.kittenml.tts.data.Paragraph
import com.kittenml.tts.data.ParagraphRepository
import com.kittenml.tts.engine.KittenTTSEngine
import com.kittenml.tts.model.TTSModel
import com.kittenml.tts.scoring.IeltsAssessment
import com.kittenml.tts.scoring.IeltsScorer
import com.kittenml.tts.scoring.ScoreResult
import com.kittenml.tts.scoring.WerScorer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PracticeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ParagraphRepository(application)
    private val asr = AsrEngineProvider.get(application)
    private val recorder = AudioRecorder()

    // TTS is reused so the learner can hear a model read the paragraph first.
    private val tts = KittenTTSEngine(application)
    private var ttsLoaded = false

    val asrState: StateFlow<AsrState> = asr.state
    val micAmplitude: StateFlow<Float> = recorder.amplitude

    private val _paragraphs = MutableStateFlow<List<Paragraph>>(emptyList())
    val paragraphs: StateFlow<List<Paragraph>> = _paragraphs

    private val _selected = MutableStateFlow<Paragraph?>(null)
    val selected: StateFlow<Paragraph?> = _selected

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _score = MutableStateFlow<ScoreResult?>(null)
    val score: StateFlow<ScoreResult?> = _score

    private val _ielts = MutableStateFlow<IeltsAssessment?>(null)
    val ielts: StateFlow<IeltsAssessment?> = _ielts

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    init {
        _paragraphs.value = repo.load()
        viewModelScope.launch { asr.load() }
    }

    fun selectParagraph(id: Int) {
        _selected.value = repo.byId(id)
        _score.value = null
        _ielts.value = null
        _status.value = ""
    }

    /** Read the reference aloud with the TTS engine (lazy-loads the Nano model). */
    fun listen() {
        val para = _selected.value ?: return
        viewModelScope.launch {
            try {
                if (!ttsLoaded) {
                    _status.value = "Loading voice…"
                    tts.loadModel(TTSModel.NANO)
                    ttsLoaded = true
                }
                _status.value = "Reading aloud…"
                val audio = tts.generate(para.text, voice = "Rosie", speed = 1.0f)
                tts.audioPlayer.play(audio)
                _status.value = ""
            } catch (e: Exception) {
                _status.value = "Listen failed: ${e.message}"
            }
        }
    }

    /** Called after the RECORD_AUDIO permission is granted. */
    fun startRecording() {
        if (_isRecording.value) return
        _score.value = null
        try {
            recorder.start()
            _isRecording.value = true
            _status.value = "Recording… read the paragraph aloud."
        } catch (e: Exception) {
            _status.value = "Microphone error: ${e.message}"
        }
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        recorder.cancel()
        _isRecording.value = false
        _status.value = ""
    }

    fun stopAndScore() {
        if (!_isRecording.value) return
        val para = _selected.value ?: return
        val audio = recorder.stop()
        _isRecording.value = false
        val durationSeconds = audio.size.toFloat() / AudioRecorder.SAMPLE_RATE

        if (audio.size < AudioRecorder.SAMPLE_RATE / 2) {
            _status.value = "Recording too short — try again."
            return
        }
        if (asr.state.value == AsrState.Unavailable) {
            _status.value = "ASR model not installed. See assets/asr/README.md."
            return
        }

        viewModelScope.launch {
            try {
                _status.value = "Transcribing…"
                val transcript = asr.transcribe(audio)
                if (transcript.isBlank()) {
                    _status.value = "Couldn't hear any speech — try again."
                    return@launch
                }
                val result = WerScorer.score(para.text, transcript)
                _score.value = result
                _ielts.value = IeltsScorer.assess(result, durationSeconds, para.band)
                _status.value = ""
            } catch (e: Exception) {
                _status.value = "Scoring failed: ${e.message}"
            }
        }
    }

    fun clearScore() {
        _score.value = null
        _ielts.value = null
        _status.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        recorder.cancel()
        tts.audioPlayer.stop()
        // asr is process-wide (AsrEngineProvider); do not close it here.
    }
}
