package com.kittenml.tts.ui.screen.practice

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kittenml.tts.asr.AsrState
import com.kittenml.tts.data.Paragraph
import com.kittenml.tts.scoring.CriterionScore
import com.kittenml.tts.scoring.IeltsAssessment
import com.kittenml.tts.scoring.ScoreResult
import com.kittenml.tts.scoring.WordOp
import com.kittenml.tts.ui.theme.*

private val CorrectColor = PrimaryAccent
private val SubColor = Color(0xFFFF9800)
private val ErrColor = Color(0xFFFF5C5C)

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val para by viewModel.selected.collectAsStateWithLifecycle()
    val asrState by viewModel.asrState.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val amplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val ielts by viewModel.ielts.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val passage = para ?: return

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "← Back",
                color = Neutral,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onBack)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(passage.title, color = Surface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "${passage.topic} · ${passage.difficultyLabel} · Band ${passage.band} · ${passage.wordCount} words",
            color = Neutral, fontSize = 12.sp, fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(16.dp))

        PassageCard(passage, score)
        Spacer(Modifier.height(16.dp))

        // Listen (TTS)
        OutlinedButton(
            onClick = { viewModel.listen() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !isRecording,
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(SecondaryAccent.copy(alpha = 0.6f))
            ),
            contentPadding = PaddingValues(vertical = 13.dp)
        ) {
            Text("🔊 Listen to a model reading", color = SecondaryAccent, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        // Record / Stop
        if (!isRecording) {
            Button(
                onClick = { onRecordClick() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryAccent, contentColor = AppBackground
                ),
                contentPadding = PaddingValues(vertical = 15.dp)
            ) {
                Text("● Record & read aloud", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                        containerColor = PrimaryAccent, contentColor = AppBackground
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
                CircularProgressIndicator(Modifier.size(16.dp), color = PrimaryAccent, strokeWidth = 2.dp)
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
                color = SubColor, fontSize = 12.sp
            )
        }

        ielts?.let {
            Spacer(Modifier.height(16.dp))
            IeltsCard(it)
        }

        score?.let {
            Spacer(Modifier.height(16.dp))
            ScoreCard(it) { viewModel.clearScore() }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PassageCard(passage: Paragraph, score: ScoreResult?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        if (score == null) {
            Text(passage.text, color = Surface, fontSize = 17.sp, lineHeight = 26.sp)
        } else {
            // Highlight the reference words by alignment outcome.
            val annotated = buildAnnotatedString {
                val refWords = score.alignment.filter { it.op != WordOp.INSERTED }
                refWords.forEachIndexed { idx, w ->
                    val color = when (w.op) {
                        WordOp.CORRECT -> CorrectColor
                        WordOp.SUBSTITUTED -> SubColor
                        WordOp.DELETED -> ErrColor
                        WordOp.INSERTED -> Surface
                    }
                    val deco = if (w.op == WordOp.DELETED) TextDecoration.LineThrough else TextDecoration.None
                    withStyle(SpanStyle(color = color, textDecoration = deco)) {
                        append(w.reference ?: "")
                    }
                    if (idx < refWords.size - 1) append(" ")
                }
            }
            Text(annotated, fontSize = 17.sp, lineHeight = 26.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(CorrectColor, "correct")
                Legend(SubColor, "misread")
                Legend(ErrColor, "skipped")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, color = Neutral, fontSize = 11.sp)
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
                    .background(PrimaryAccent, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun ScoreCard(score: ScoreResult, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, PrimaryAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${score.accuracyPercent}%",
                color = PrimaryAccent, fontSize = 40.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("reading accuracy", color = Neutral, fontSize = 13.sp)
                Text(
                    "≈ Band ${score.estimatedBand} · WER ${(score.wer * 100).toInt()}%",
                    color = Surface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("✓", score.correct, "correct", CorrectColor)
            Stat("≠", score.substitutions, "misread", SubColor)
            Stat("–", score.deletions, "skipped", ErrColor)
            Stat("+", score.insertions, "extra", SecondaryAccent)
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = DarkAccent.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))
        Text("We heard", color = Neutral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(score.transcript.ifBlank { "(nothing)" }, color = Surface, fontSize = 14.sp, lineHeight = 21.sp)
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
private fun IeltsCard(assessment: IeltsAssessment) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, SecondaryAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("IELTS band estimate", color = Neutral, fontSize = 13.sp)
                Text(
                    "Overall ${assessment.overall}",
                    color = SecondaryAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        assessment.criteria.forEach { c ->
            CriterionRow(c)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "Estimated from reading accuracy, coverage and pace. A read-aloud task " +
                "can only approximate the official speaking criteria.",
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
private fun RowScope.Stat(symbol: String, count: Int, label: String, color: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(DarkAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$symbol $count", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Neutral, fontSize = 10.sp)
    }
}
