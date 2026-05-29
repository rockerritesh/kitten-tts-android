package com.kittenml.tts.ui.screen.speaking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kittenml.tts.asr.AsrEngineProvider
import com.kittenml.tts.asr.AsrState
import com.kittenml.tts.asr.AudioRecorder
import com.kittenml.tts.data.SpeakingPrompt
import com.kittenml.tts.data.SpeakingPromptRepository
import com.kittenml.tts.scoring.FluencyAnalyzer
import com.kittenml.tts.scoring.FreeSpeechResult
import com.kittenml.tts.scoring.FreeSpeechScorer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SpeakingViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SpeakingPromptRepository(application)
    private val asr = AsrEngineProvider.get(application)
    private val recorder = AudioRecorder()

    val asrState: StateFlow<AsrState> = asr.state
    val micAmplitude: StateFlow<Float> = recorder.amplitude

    private val _prompts = MutableStateFlow<List<SpeakingPrompt>>(emptyList())
    val prompts: StateFlow<List<SpeakingPrompt>> = _prompts

    private val _selected = MutableStateFlow<SpeakingPrompt?>(null)
    val selected: StateFlow<SpeakingPrompt?> = _selected

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _result = MutableStateFlow<FreeSpeechResult?>(null)
    val result: StateFlow<FreeSpeechResult?> = _result

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    init {
        _prompts.value = repo.load()
        viewModelScope.launch { asr.load() }
    }

    fun selectPrompt(id: Int) {
        _selected.value = repo.byId(id)
        _result.value = null
        _status.value = ""
    }

    fun startRecording() {
        if (_isRecording.value) return
        _result.value = null
        try {
            recorder.start()
            _isRecording.value = true
            _status.value = "Recording… answer the question."
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
        val prompt = _selected.value ?: return
        val audio = recorder.stop()
        _isRecording.value = false

        if (audio.size < AudioRecorder.SAMPLE_RATE) {
            _status.value = "Answer too short — try to speak for longer."
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
                val metrics = FluencyAnalyzer.analyze(audio, transcript)
                _result.value = FreeSpeechScorer.assess(
                    transcript = transcript,
                    metrics = metrics,
                    keywords = prompt.keywordPoints,
                    expectedSeconds = prompt.expectedSeconds
                )
                _status.value = ""
            } catch (e: Exception) {
                _status.value = "Scoring failed: ${e.message}"
            }
        }
    }

    fun clearResult() {
        _result.value = null
        _status.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        recorder.cancel()
        // asr is process-wide (AsrEngineProvider); do not close it here.
    }
}
