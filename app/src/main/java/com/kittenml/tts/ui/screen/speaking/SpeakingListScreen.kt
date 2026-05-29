package com.kittenml.tts.ui.screen.speaking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kittenml.tts.data.SpeakingPrompt
import com.kittenml.tts.ui.theme.*

@Composable
fun SpeakingListScreen(
    prompts: List<SpeakingPrompt>,
    onSelect: (Int) -> Unit
) {
    var part by remember { mutableStateOf(0) } // 0 = all
    val filters = listOf(0 to "All parts", 1 to "Part 1", 2 to "Part 2", 3 to "Part 3")
    val visible = remember(prompts, part) {
        if (part == 0) prompts else prompts.filter { it.part == part }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Free Speaking", color = Surface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Answer a prompt in your own words — scored on the four IELTS criteria.",
            color = Neutral, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((value, label) in filters) {
                FilterChip(label, value == part) { part = value }
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.id }) { p ->
                PromptCard(p) { onSelect(p.id) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) AppBackground else Surface,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) Modifier.background(SecondaryAccent, RoundedCornerShape(50))
                else Modifier.border(1.dp, DarkAccent.copy(alpha = 0.5f), RoundedCornerShape(50))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun PromptCard(p: SpeakingPrompt, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                p.partLabel,
                color = AppBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(SecondaryAccent, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Text(p.topic, color = Neutral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(p.prompt, color = Surface, fontSize = 15.sp, lineHeight = 21.sp)
    }
}
