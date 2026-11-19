package com.dayline.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The pictures on empty pages, drawn by tools/empty-art/gen.py (the same pictures as on the Mac). */
enum class EmptyArtKind { INBOX, CALENDAR, CHECKLIST, SEARCH, SPROUT, NOTEBOOK, JOURNAL, CHART, HELLO;

    companion object {
        /** The picture for an empty page that used to show [icon]. */
        fun forIcon(icon: ImageVector): EmptyArtKind {
            val n = icon.name.substringAfterLast('.')
            return when {
                n.contains("Inbox") -> INBOX
                n.contains("Upcoming") || n.contains("Event") || n.contains("Calendar") -> CALENDAR
                n.contains("DoneAll") || n.contains("TaskAlt") || n.contains("Checklist") -> CHECKLIST
                n.contains("Search") || n.contains("FilterList") -> SEARCH
                n.contains("SelfImprovement") || n.contains("Spa") || n.contains("Eco") -> SPROUT
                n.contains("Lightbulb") || n.contains("Note") -> NOTEBOOK
                n.contains("AutoStories") || n.contains("Book") -> JOURNAL
                n.contains("Insights") || n.contains("BarChart") -> CHART
                else -> HELLO
            }
        }
    }
}

private class ArtElement(val fill: Char?, val stroke: Char?, val width: Float, val path: Path)

private val parsed = HashMap<EmptyArtKind, List<ArtElement>>()

private fun elements(kind: EmptyArtKind): List<ArtElement> = parsed.getOrPut(kind) {
    EmptyArtData.scenes[kind.name.lowercase()].orEmpty().split('|').mapNotNull { e ->
        val colon = e.indexOf(':')
        if (colon < 2) return@mapNotNull null
        ArtElement(
            fill = e[0].takeIf { it != '-' },
            stroke = e[1].takeIf { it != '-' },
            width = e.substring(2, colon).toFloatOrNull() ?: 2f,
            path = PathParser().parsePathString(e.substring(colon + 1)).toPath(),
        )
    }
}

/** A small still life drawn in outlines, [width] wide (light lines in dark mode). */
@Composable
fun EmptyArt(kind: EmptyArtKind, modifier: Modifier = Modifier, width: Dp = 256.dp) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colors = remember(dark) { EmptyArtData.palette.mapValues { (_, c) -> Color(if (dark) c.second else c.first) } }
    val els = remember(kind) { elements(kind) }
    // Drawn in its own layer, so a cut erases only the shapes drawn before it, never the page behind.
    Canvas(modifier.size(width, width * EmptyArtData.HEIGHT / EmptyArtData.WIDTH).clipToBounds().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val k = this.size.width / EmptyArtData.WIDTH
        scale(k, k, pivot = Offset.Zero) {
            els.forEach { e ->
                val stroke = Stroke(e.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                if (e.fill == 'x' || e.stroke == 'x') {
                    if (e.fill == 'x') drawPath(e.path, Color.Black, blendMode = BlendMode.Clear)
                    if (e.stroke == 'x') drawPath(e.path, Color.Black, style = stroke, blendMode = BlendMode.Clear)
                    return@forEach
                }
                e.fill?.let { r -> colors[r]?.let { drawPath(e.path, it) } }
                e.stroke?.let { r -> colors[r]?.let { drawPath(e.path, it, style = stroke) } }
            }
        }
    }
}
