package com.kittenml.tts.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kittenml.tts.engine.AudioPlayer
import com.kittenml.tts.engine.KittenTTSEngine
import com.kittenml.tts.model.EngineState
import com.kittenml.tts.model.TTSModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TTSViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = KittenTTSEngine(application)
    val engineState: StateFlow<EngineState> = engine.state

    private val _selectedModel = MutableStateFlow(TTSModel.MINI)
    val selectedModel: StateFlow<TTSModel> = _selectedModel

    private val _selectedVoice = MutableStateFlow("Rosie")
    val selectedVoice: StateFlow<String> = _selectedVoice

    private val _inputText = MutableStateFlow("Destiny one is the best video game of all time. There is no denying it.")
    val inputText: StateFlow<String> = _inputText

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed

    private val _generatedAudio = MutableStateFlow<FloatArray?>(null)
    val generatedAudio: StateFlow<FloatArray?> = _generatedAudio

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    init {
        loadModel(TTSModel.MINI)
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun selectVoice(voice: String) {
        _selectedVoice.value = voice
    }

    fun selectModel(model: TTSModel) {
        if (_selectedModel.value == model) return
        _selectedModel.value = model
        _generatedAudio.value = null
        _statusMessage.value = ""
        loadModel(model)
    }

    fun updateSpeed(speed: Float) {
        _speed.value = speed
    }

    private fun loadModel(model: TTSModel) {
        viewModelScope.launch {
            engine.loadModel(model)
        }
    }

    fun generate() {
        viewModelScope.launch {
            _generatedAudio.value = null
            _statusMessage.value = ""

            try {
                val startTime = System.nanoTime()
                val audio = engine.generate(
                    text = _inputText.value,
                    voice = _selectedVoice.value,
                    speed = _speed.value
                )
                val elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0
                val duration = audio.size.toFloat() / AudioPlayer.SAMPLE_RATE

                _generatedAudio.value = audio
                _statusMessage.value = String.format("%.1fs audio in %.2fs", duration, elapsed)
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun play() {
        val audio = _generatedAudio.value ?: return
        engine.audioPlayer.play(audio)
    }

    fun download() {
        val audio = _generatedAudio.value ?: return
        val timestamp = System.currentTimeMillis() / 1000
        val fileName = "kitten_tts_${_selectedVoice.value.lowercase()}_$timestamp"
        val result = AudioPlayer.saveAsWav(getApplication(), audio, fileName)
        if (result != null) {
            _statusMessage.value = "Saved to Downloads/$result"
        } else {
            _statusMessage.value = "Failed to save audio"
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.audioPlayer.stop()
    }
}
