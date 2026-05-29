package com.kittenml.tts.ui.screen.speaking

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kittenml.tts.asr.AsrState
import com.kittenml.tts.data.SpeakingPrompt
import com.kittenml.tts.scoring.CriterionScore
import com.kittenml.tts.scoring.FreeSpeechResult
import com.kittenml.tts.scoring.IeltsAssessment
import com.kittenml.tts.ui.theme.*

private val ErrColor = Color(0xFFFF5C5C)

@Composable
fun SpeakingScreen(
    viewModel: SpeakingViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prompt by viewModel.selected.collectAsStateWithLifecycle()
    val asrState by viewModel.asrState.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val amplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val p = prompt ?: return

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording() }

    fun onRecordClick() {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording()
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "← Back",
            color = Neutral, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onBack)
        )
        Spacer(Modifier.height(12.dp))
        Text("${p.partLabel} · ${p.topic}", color = Neutral, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))

        PromptCard(p)
        Spacer(Modifier.height(16.dp))

        if (!isRecording) {
            Button(
                onClick = { onRecordClick() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryAccent, contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 15.dp)
            ) {
                Text("● Record your answer", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            MicMeter(amplitude)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.stopAndScore() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SecondaryAccent, contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 15.dp)
                ) {
                    Text("Stop & Score", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { viewModel.cancelRecording() },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 15.dp, horizontal = 18.dp)
                ) {
                    Text("Cancel", color = Neutral, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (asrState == AsrState.Transcribing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = SecondaryAccent, strokeWidth = 2.dp)
                Text("Transcribing…", color = Neutral, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        } else if (status.isNotEmpty()) {
            Text(status, color = Neutral, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        if (asrState == AsrState.Unavailable) {
            Spacer(Modifier.height(8.dp))
            Text(
                "ASR model not installed yet. Recording works, but scoring needs the " +
                    "Whisper ONNX files in assets/asr/ (see README).",
                color = Color(0xFFFF9800), fontSize = 12.sp
            )
        }

        result?.let {
            Spacer(Modifier.height(16.dp))
            BandCard(it.assessment)
            Spacer(Modifier.height(16.dp))
            FeedbackCard(it) { viewModel.clearResult() }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PromptCard(p: SpeakingPrompt) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(p.prompt, color = Surface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp)
        if (p.cuePoints.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("You should say:", color = Neutral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            p.cuePoints.forEach { cue ->
                Text("•  $cue", color = Surface, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Speak for about ${p.expectedSeconds} seconds.",
            color = SecondaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MicMeter(amplitude: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(12.dp))
            .border(1.dp, ErrColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text("● Recording", color = ErrColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(amplitude.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .background(SecondaryAccent, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun BandCard(assessment: IeltsAssessment) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, SecondaryAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text("IELTS band estimate", color = Neutral, fontSize = 13.sp)
        Text(
            "Overall ${assessment.overall}",
            color = SecondaryAccent, fontSize = 30.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        assessment.criteria.forEach { c ->
            CriterionRow(c)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "On-device heuristic estimate — it rewards range and delivery but cannot " +
                "judge meaning like an examiner. Use it for practice, not as an official score.",
            color = Neutral, fontSize = 11.sp, lineHeight = 15.sp
        )
    }
}

@Composable
private fun CriterionRow(c: CriterionScore) {
    val fraction = (c.band / 9f).coerceIn(0f, 1f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(c.name, color = Surface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${c.band}",
                color = SecondaryAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(SecondaryAccent, RoundedCornerShape(50))
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(c.detail, color = Neutral, fontSize = 11.sp)
    }
}

@Composable
private fun FeedbackCard(result: FreeSpeechResult, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        if (result.keywordsHit.isNotEmpty() || result.keywordsMissed.isNotEmpty()) {
            Text("Points covered", color = Neutral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowChips(result.keywordsHit, PrimaryAccent, AppBackground)
            if (result.keywordsMissed.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Try to include:", color = Neutral, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                FlowChips(result.keywordsMissed, DarkAccent.copy(alpha = 0.4f), Surface)
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = DarkAccent.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
        }

        Text("What we heard", color = Neutral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(result.transcript.ifBlank { "(nothing)" }, color = Surface, fontSize = 14.sp, lineHeight = 21.sp)

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SecondaryAccent, contentColor = Color.White),
            contentPadding = PaddingValues(vertical = 13.dp)
        ) {
            Text("Try again", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FlowChips(items: List<String>, bg: Color, fg: Color) {
    // Simple wrapping rows of chips.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { item ->
                    Text(
                        item,
                        color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(bg, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
