package com.dayline.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayline.app.core.domain.NoteFormat
import com.dayline.app.ui.theme.DaylineTheme

/** Accent of a callout kind (same hues as the Mac). */
fun calloutAccent(kind: String?): Color = when (kind) {
    "tip" -> Color(0xFF29A055)
    "important" -> Color(0xFF8754DB)
    "warning" -> Color(0xFFD98C00)
    "caution" -> Color(0xFFD93833)
    else -> Color(0xFF1A73E8)
}

fun calloutName(kind: String?): String = kind?.replaceFirstChar { it.uppercase() } ?: "Quote"

/** A small ⋮ menu beside a block. */
@Composable
private fun BlockMenu(items: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.MoreVert, "Block options", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }) {
            items.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() }) }
        }
    }
}

/** A quote (bar on the left) or a callout (tinted box), with formatted text inside. */
@Composable
internal fun QuoteBlock(
    body: NoteBodyModel,
    seg: NoteBodyModel.QuoteSeg,
    style: TextStyle,
    readOnly: Boolean,
    onAddLabels: (List<String>) -> Unit,
) {
    val callout = seg.calloutKind
    val accent = if (callout != null) calloutAccent(callout) else MaterialTheme.colorScheme.outline
    val fill = if (callout != null) accent.copy(alpha = if (DaylineTheme.isDark) 0.18f else 0.10f) else Color.Transparent
    val bar = if (callout != null) 4.dp else 3.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(if (callout != null) 10.dp else 2.dp))
            .background(fill)
            // The bar is drawn, not measured: the rich text editor can't be asked for intrinsic sizes.
            .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(bar.toPx(), size.height)) },
    ) {
        Column(Modifier.weight(1f).padding(start = 12.dp + bar, top = if (callout != null) 8.dp else 2.dp, bottom = if (callout != null) 8.dp else 2.dp)) {
            if (callout != null) {
                Text(calloutName(callout), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
            }
            TextSegmentEditor(body, seg, style.copy(color = if (callout == null) MaterialTheme.colorScheme.onSurfaceVariant else style.color), 24.dp, null, onAddLabels, readOnly = readOnly)
        }
        if (!readOnly) {
            BlockMenu(
                listOf("Quote" to { seg.kind = "quote" }) +
                    NoteFormat.CALLOUT_KINDS.map { k -> "Callout: ${calloutName(k)}" to { seg.kind = "callout:$k" } } +
                    listOf("Turn into text" to { body.unwrap(seg) }, "Delete" to { body.remove(seg) }),
                Modifier.align(Alignment.Top),
            )
        }
    }
}

/** A code block: plain monospaced text that keeps its spacing. */
@Composable
internal fun CodeBlock(body: NoteBodyModel, seg: NoteBodyModel.CodeSeg, readOnly: Boolean) {
    val clipboard = LocalClipboardManager.current
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (DaylineTheme.isDark) 0.09f else 0.05f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = seg.language,
                onValueChange = { seg.language = it.filter { c -> c.isLetterOrDigit() || c in "+#-_." }.take(20) },
                readOnly = readOnly,
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> Box { if (seg.language.isEmpty()) Text("Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } },
            )
            BlockMenu(
                buildList {
                    add("Copy code" to { clipboard.setText(AnnotatedString(seg.text)) })
                    if (!readOnly) { add("Turn into text" to { body.unwrap(seg) }); add("Delete" to { body.remove(seg) }) }
                },
            )
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            BasicTextField(
                value = seg.text,
                onValueChange = { seg.text = it },
                readOnly = readOnly,
                textStyle = mono,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.widthIn(min = 240.dp).heightIn(min = 20.dp),
            )
        }
    }
}

/** A table you type into cell by cell; ⋮ adds or removes rows and columns. */
@Composable
internal fun TableBlock(body: NoteBodyModel, seg: NoteBodyModel.TableSeg, readOnly: Boolean) {
    var focusRow by remember { mutableIntStateOf(0) }
    var focusCol by remember { mutableIntStateOf(0) }
    val line = MaterialTheme.colorScheme.outlineVariant
    val cellStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Column(Modifier.border(1.dp, line, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) {
                seg.rows.forEachIndexed { r, row ->
                    val header = seg.header && r == 0
                    Row(
                        Modifier.height(IntrinsicSize.Min)
                            .background(if (header) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
                            // Row lines are drawn by each row (a divider has no width inside a sideways-scrolling table).
                            .drawBehind { if (r > 0) drawRect(line, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) },
                    ) {
                        for (c in 0 until seg.columns) {
                            if (c > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(line))
                            BasicTextField(
                                value = row.getOrElse(c) { "" },
                                onValueChange = { body.setCell(seg, r, c, it) },
                                readOnly = readOnly,
                                singleLine = false,
                                textStyle = cellStyle.copy(fontWeight = if (header) FontWeight.SemiBold else null),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .width(130.dp)
                                    .onFocusChanged { if (it.isFocused) { focusRow = r; focusCol = c } }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }
        if (!readOnly) {
            BlockMenu(
                listOf(
                    "Add row" to { body.addRow(seg, focusRow) },
                    "Add column" to { body.addColumn(seg, focusCol) },
                    "Delete row" to { body.deleteRow(seg, focusRow); focusRow = 0 },
                    "Delete column" to { body.deleteColumn(seg, focusCol); focusCol = 0 },
                    (if (seg.header) "Header row off" else "Header row on") to { seg.header = !seg.header },
                    "Turn into text" to { body.unwrap(seg) },
                    "Delete table" to { body.remove(seg) },
                ),
            )
        }
    }
}

/** A line across the note. */
@Composable
internal fun DividerBlock(body: NoteBodyModel, seg: NoteBodyModel.DividerSeg, readOnly: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        if (!readOnly) BlockMenu(listOf("Delete line" to { body.remove(seg) }))
    }
}
