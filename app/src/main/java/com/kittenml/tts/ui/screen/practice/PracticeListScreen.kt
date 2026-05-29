package com.kittenml.tts.ui.screen.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kittenml.tts.data.Paragraph
import com.kittenml.tts.ui.theme.*

@Composable
fun PracticeListScreen(
    paragraphs: List<Paragraph>,
    onSelect: (Int) -> Unit
) {
    var difficulty by remember { mutableStateOf("All") }
    val filters = listOf("All", "Beginner", "Intermediate", "Advanced")
    val visible = remember(paragraphs, difficulty) {
        if (difficulty == "All") paragraphs
        else paragraphs.filter { it.difficultyLabel == difficulty }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("IELTS Reading", color = Surface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Read each passage aloud — we'll score your accuracy.",
            color = Neutral, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (f in filters) {
                FilterChip(f, f == difficulty) { difficulty = f }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${visible.size} passages",
            color = Neutral, fontSize = 12.sp, fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.id }) { para ->
                ParagraphCard(para) { onSelect(para.id) }
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
                if (selected) Modifier.background(PrimaryAccent, RoundedCornerShape(50))
                else Modifier.border(1.dp, DarkAccent.copy(alpha = 0.5f), RoundedCornerShape(50))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun ParagraphCard(para: Paragraph, onClick: () -> Unit) {
    val dotColor = when (para.difficultyLabel) {
        "Beginner" -> PrimaryAccent
        "Intermediate" -> Color(0xFFFF9800)
        else -> SecondaryAccent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, DarkAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Text(para.title, color = Surface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            para.text,
            color = Neutral, fontSize = 13.sp, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DarkAccent.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetaPill(para.topic)
            MetaPill("${para.difficultyLabel} · Band ${para.band}")
            MetaPill("${para.wordCount} words")
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Text(
        text,
        color = Neutral,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(DarkAccent.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
