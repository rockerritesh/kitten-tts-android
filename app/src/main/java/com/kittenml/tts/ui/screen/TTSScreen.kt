package com.kittenml.tts.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kittenml.tts.model.EngineState
import com.kittenml.tts.model.TTSModel
import com.kittenml.tts.ui.theme.*

@Composable
fun TTSScreen(viewModel: TTSViewModel = viewModel()) {
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val generatedAudio by viewModel.generatedAudio.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Header
        Header(engineState, selectedModel)

        Spacer(Modifier.height(24.dp))

        // Text Input
        TextInputCard(inputText) { viewModel.updateInputText(it) }

        Spacer(Modifier.height(16.dp))

        // Voice & Model
        VoiceAndModelCard(
            selectedVoice = selectedVoice,
            onVoiceSelected = { viewModel.selectVoice(it) },
            selectedModel = selectedModel,
            onModelSelected = { viewModel.selectModel(it) }
        )

        Spacer(Modifier.height(16.dp))

        // Speed
        SpeedCard(speed) { viewModel.updateSpeed(it) }

        Spacer(Modifier.height(24.dp))

        // Action Buttons
        ActionButtons(
            engineState = engineState,
            hasAudio = generatedAudio != null,
            inputEmpty = inputText.isBlank(),
            onGenerate = { viewModel.generate() },
            onPlay = { viewModel.play() }
        )

        Spacer(Modifier.height(12.dp))

        // Status
        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                color = Neutral,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header(state: EngineState, selectedModel: TTSModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Kitten TTS",
                color = Surface,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "On-device text to speech",
                color = Neutral,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Status pill
        val (statusText, statusColor) = when (state) {
            is EngineState.Idle -> "Idle" to DarkAccent
            is EngineState.Loading -> "Loading\u2026" to Color(0xFFFF9800)
            is EngineState.Ready -> selectedModel.displayName to PrimaryAccent
            is EngineState.Generating -> "Generating\u2026" to Color(0xFFFF9800)
            is EngineState.Error -> "Error" to Color.Red
        }

        Row(
            modifier = Modifier
                .background(CardBg, RoundedCornerShape(50))
                .border(1.dp, DarkAccent.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                statusText,
                color = Neutral,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TextInputCard(text: String, onTextChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            "Text",
            color = Neutral,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 140.dp),
            placeholder = {
                Text(
                    "Enter text to be spoken aloud\u2026",
                    color = DarkAccent
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Surface,
                unfocusedTextColor = Surface,
                cursorColor = PrimaryAccent,
                focusedBorderColor = PrimaryAccent.copy(alpha = 0.4f),
                unfocusedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun VoiceAndModelCard(
    selectedVoice: String,
    onVoiceSelected: (String) -> Unit,
    selectedModel: TTSModel,
    onModelSelected: (TTSModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
    ) {
        // Voice section
        Column(Modifier.padding(14.dp)) {
            Text("Voice", color = Neutral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (voice in TTSModel.voiceNames) {
                    VoiceChip(voice, voice == selectedVoice) { onVoiceSelected(voice) }
                }
            }
        }

        HorizontalDivider(color = DarkAccent.copy(alpha = 0.4f))

        // Model section
        Column(Modifier.padding(14.dp)) {
            Text("Model", color = Neutral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (model in TTSModel.entries) {
                    ModelChip(model.displayName, model == selectedModel) { onModelSelected(model) }
                }
            }
        }
    }
}

@Composable
private fun VoiceChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = name,
        color = if (isSelected) AppBackground else Surface,
        fontSize = 13.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (isSelected) Modifier.background(PrimaryAccent, RoundedCornerShape(50))
                else Modifier
                    .border(1.dp, DarkAccent.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .background(DarkAccent.copy(alpha = 0.35f), RoundedCornerShape(50))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun ModelChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = name,
        color = if (isSelected) Color.White else Neutral,
        fontSize = 12.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (isSelected) Modifier.background(SecondaryAccent, RoundedCornerShape(50))
                else Modifier.border(1.dp, DarkAccent.copy(alpha = 0.5f), RoundedCornerShape(50))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun SpeedCard(speed: Float, onSpeedChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Speed", color = Neutral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                String.format("%.1fx", speed),
                color = PrimaryAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(10.dp))
        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            valueRange = 0.5f..2.0f,
            steps = 14,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryAccent,
                activeTrackColor = PrimaryAccent
            )
        )
    }
}

@Composable
private fun ActionButtons(
    engineState: EngineState,
    hasAudio: Boolean,
    inputEmpty: Boolean,
    onGenerate: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val isReady = engineState is EngineState.Ready
        val isGenerating = engineState is EngineState.Generating

        // Generate button
        Button(
            onClick = onGenerate,
            enabled = isReady && !inputEmpty,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isReady) PrimaryAccent else DarkAccent.copy(alpha = 0.6f),
                contentColor = if (isReady) AppBackground else Neutral,
                disabledContainerColor = DarkAccent.copy(alpha = 0.6f),
                disabledContentColor = Neutral
            ),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AppBackground,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isGenerating) "Generating\u2026" else "Generate",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Play button
        if (hasAudio) {
            Button(
                onClick = onPlay,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryAccent,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text("Play", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
