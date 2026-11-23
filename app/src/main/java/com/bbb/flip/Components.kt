package com.bbb.flip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Gold = Color(0xFFF0B90B)
val Good = Color(0xFF4ADE80)
val Bad = Color(0xFFFF6B6B)
val Dim = Color(0xFF8B97A8)
val Panel = Color(0xFF161B24)
val PanelAlt = Color(0xFF1D2431)

fun qualityColor(quality: String?): Color = when (quality?.lowercase()) {    "poor" -> Color(0xFF9D9D9D)
    "uncommon" -> Color(0xFF1EFF00)
    "rare" -> Color(0xFF3B9EFF)
    "epic" -> Color(0xFFA335EE)
    "legendary" -> Color(0xFFFF8000)
    "artifact" -> Color(0xFFE6CC80)
    else -> Color(0xFFE7E9EE)
}

@Composable
fun ItemIcon(url: String?, quality: String?, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF0B0F14))
            .border(1.dp, qualityColor(quality).copy(alpha = 0.8f), RoundedCornerShape(7.dp)),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun Money(
    copper: Long?,
    color: Color = Color.Unspecified,
    bold: Boolean = false,
    fontSize: Int = 13,
) {
    Text(
        text = Money.full(copper),
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        fontSize = fontSize.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
    )
}

@Composable
fun Caption(text: String, color: Color = Dim, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Gold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = modifier,
    )
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Dim, fontSize = 13.sp)
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Caption(label)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ChipsRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Small coloured pill used for ROI / discount / margin. */
@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ListHeader(text: String) {
    Spacer(Modifier.height(4.dp))
    SectionTitle(text)
    Spacer(Modifier.height(2.dp))
}

val ListPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp)

@Composable
fun Spacer8() = Spacer(Modifier.height(8.dp))

@Composable
fun HSpacer(w: Dp) = Spacer(Modifier.width(w))

/** Russian item name when known, otherwise the name the game client shows. */
@Composable
fun ruName(id: Long?, fallback: String): String =
    remember(id, RuNames.version) { RuNames.get(id) ?: fallback }

/** API verdict codes ("current", "stale", …) shown in Russian. */
fun verdictLabel(verdict: String?): String = when (verdict) {
    "current" -> "актуален"
    "stale" -> "устарел"
    "old" -> "старый"
    "missing" -> "нет данных"
    null, "" -> "—"
    else -> verdict
}
