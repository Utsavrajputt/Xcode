package com.invictus.xcode.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.invictus.xcode.R
import com.invictus.xcode.core.git.model.GitDiffLineType
import com.invictus.xcode.core.git.model.GitDiffRow
import com.invictus.xcode.core.git.model.GitFileDiffResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * GitHub-style diff viewer: unified ("Inline") or split ("Side by side"), old/new line-number
 * gutters, +/- sign column, full-width add/remove tints with stronger word-level highlight, and
 * blue "@@ ... @@" hunk headers. Binary notice and old-vs-new image comparison are kept.
 */
@Composable
fun GitDiffViewer(result: GitFileDiffResult, modifier: Modifier = Modifier) {
    var sideBySide by rememberSaveable { mutableStateOf(false) }
    val added = remember(result) { result.rows.count { it.rightType == GitDiffLineType.ADDED } }
    val removed = remember(result) { result.rows.count { it.leftType == GitDiffLineType.REMOVED } }
    Column(modifier = modifier.fillMaxWidth()) {
        DiffToolbar(
            sideBySide = sideBySide,
            onChange = { sideBySide = it },
            added = added,
            removed = removed,
            showStats = !result.isBinary && !result.isImage,
        )
        when {
            result.isImage -> GitImageDiff(result)
            result.isBinary -> Text(
                text = stringResource(R.string.git_diff_binary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else -> DiffRows(result = result, sideBySide = sideBySide)
        }
        if (result.truncated) {
            Text(
                text = stringResource(R.string.git_diff_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

// ---- palette (GitHub dark / light) ---------------------------------------------------------

private class DiffPalette(
    val addLine: Color, val addGutter: Color, val addWord: Color,
    val delLine: Color, val delGutter: Color, val delWord: Color,
    val hunkBg: Color, val hunkText: Color,
    val emptyBg: Color, val number: Color, val divider: Color,
    val addAccent: Color, val delAccent: Color,
)

@Composable
private fun rememberPalette(): DiffPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return remember(dark, scheme.onSurface, scheme.onSurfaceVariant) {
        if (dark) {
            val green = Color(0xFF2EA043)
            val red = Color(0xFFF85149)
            val blue = Color(0xFF388BFD)
            DiffPalette(
                addLine = green.copy(alpha = 0.15f), addGutter = green.copy(alpha = 0.30f), addWord = green.copy(alpha = 0.40f),
                delLine = red.copy(alpha = 0.15f), delGutter = red.copy(alpha = 0.30f), delWord = red.copy(alpha = 0.40f),
                hunkBg = blue.copy(alpha = 0.15f), hunkText = Color(0xFF9198A1),
                emptyBg = scheme.onSurface.copy(alpha = 0.04f), number = Color(0xFF8B949E),
                divider = scheme.onSurface.copy(alpha = 0.12f),
                addAccent = Color(0xFF3FB950), delAccent = Color(0xFFF85149),
            )
        } else {
            DiffPalette(
                addLine = Color(0xFFE6FFEC), addGutter = Color(0xFFCCFFD8), addWord = Color(0xFFABF2BC),
                delLine = Color(0xFFFFEBE9), delGutter = Color(0xFFFFD7D5), delWord = Color(0xFFFFC1BC),
                hunkBg = Color(0xFFDDF4FF), hunkText = Color(0xFF57606A),
                emptyBg = scheme.onSurface.copy(alpha = 0.04f), number = Color(0xFF57606A),
                divider = scheme.onSurface.copy(alpha = 0.12f),
                addAccent = Color(0xFF1A7F37), delAccent = Color(0xFFCF222E),
            )
        }
    }
}

// ---- toolbar: segmented Inline / Side by side + "+N -M" with GitHub's 5-block bar -----------

@Composable
private fun DiffToolbar(sideBySide: Boolean, onChange: (Boolean) -> Unit, added: Int, removed: Int, showStats: Boolean) {
    val p = rememberPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, p.divider, RoundedCornerShape(8.dp)),
        ) {
            Segment(stringResource(R.string.git_diff_inline), selected = !sideBySide) { onChange(false) }
            Box(Modifier.width(1.dp).height(30.dp).background(p.divider))
            Segment(stringResource(R.string.git_diff_side_by_side), selected = sideBySide) { onChange(true) }
        }
        Spacer(Modifier.weight(1f))
        if (showStats && (added + removed) > 0) {
            Text("+$added", color = p.addAccent, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Text("\u2212$removed", color = p.delAccent, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            StatBlocks(added, removed, p)
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(30.dp)
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** GitHub's five little squares: share of green / red / grey by change proportion. */
@Composable
private fun StatBlocks(added: Int, removed: Int, p: DiffPalette) {
    val total = (added + removed).coerceAtLeast(1)
    var green = Math.round(5f * added / total)
    var red = Math.round(5f * removed / total)
    if (added > 0 && green == 0) green = 1
    if (removed > 0 && red == 0) red = 1
    while (green + red > 5) { if (green >= red) green-- else red-- }
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
        repeat(5) { i ->
            val c = when {
                i < green -> p.addAccent
                i < green + red -> p.delAccent
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            }
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(1.5.dp)).background(c))
        }
    }
}

// ---- rows --------------------------------------------------------------------------------

/** One line of the unified view. [type] HUNK carries the "@@ ... @@" header in [text]. */
private data class UniLine(
    val oldNo: Int?,
    val newNo: Int?,
    val text: String,
    val type: GitDiffLineType,
    val ranges: List<IntRange>,
)

/** Unified order, like GitHub: within a change block all removed lines first, then all added. */
private fun toUnified(rows: List<GitDiffRow>): List<UniLine> {
    val out = ArrayList<UniLine>(rows.size + 16)
    val rem = ArrayList<UniLine>()
    val add = ArrayList<UniLine>()
    fun flush() {
        out += rem; out += add
        rem.clear(); add.clear()
    }
    for (r in rows) {
        when {
            r.leftType == GitDiffLineType.HUNK -> { flush(); out += UniLine(null, null, r.leftText.orEmpty(), GitDiffLineType.HUNK, emptyList()) }
            r.leftType == GitDiffLineType.CONTEXT -> { flush(); out += UniLine(r.leftNumber, r.rightNumber, r.leftText.orEmpty(), GitDiffLineType.CONTEXT, emptyList()) }
            else -> {
                if (r.leftType == GitDiffLineType.REMOVED) rem += UniLine(r.leftNumber, null, r.leftText.orEmpty(), GitDiffLineType.REMOVED, r.intralineLeft)
                if (r.rightType == GitDiffLineType.ADDED) add += UniLine(null, r.rightNumber, r.rightText.orEmpty(), GitDiffLineType.ADDED, r.intralineRight)
            }
        }
    }
    flush()
    return out
}

private const val MAX_DIFF_CHARS = 1000
private val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
private val SignWidth = 18.dp
private val CodePad = 8.dp

/**
 * One shared horizontal scroll for the whole diff (every row moves together) and a vertical
 * LazyColumn inside it; rows are exactly content-width so tints and gutters run edge to edge.
 */
@Composable
private fun DiffRows(result: GitFileDiffResult, sideBySide: Boolean) {
    val p = rememberPalette()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidth = remember(density) { with(density) { measurer.measure("0", CodeStyle).size.width.toDp() } }

    val unified = remember(result) { toUnified(result.rows) }
    val maxChars = remember(result) {
        var m = 0
        result.rows.forEach { r -> m = maxOf(m, r.leftText?.length ?: 0, r.rightText?.length ?: 0) }
        m.coerceAtMost(MAX_DIFF_CHARS)
    }
    val maxNo = remember(result) {
        var m = 1
        result.rows.forEach { r -> m = maxOf(m, r.leftNumber ?: 0, r.rightNumber ?: 0) }
        m
    }
    val numWidth = charWidth * maxNo.toString().length.coerceAtLeast(3) + 20.dp
    val codeWidth = charWidth * (maxChars + 2) + CodePad * 2

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxWidth
        val halfWidth = numWidth + SignWidth + codeWidth
        val contentWidth = if (sideBySide) maxOf(viewport, halfWidth * 2 + 1.dp) else maxOf(viewport, numWidth * 2 + SignWidth + codeWidth)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            if (sideBySide) {
                LazyColumn(Modifier.width(contentWidth).fillMaxHeight()) {
                    items(result.rows.size, key = { it }) { i ->
                        val row = result.rows[i]
                        if (row.leftType == GitDiffLineType.HUNK) {
                            HunkRow(row.leftText.orEmpty(), gutterWidth = numWidth, p = p)
                        } else {
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                SplitCell(row.leftNumber, row.leftText, row.leftType, row.intralineLeft, numWidth, p, Modifier.weight(1f))
                                Box(Modifier.width(1.dp).fillMaxHeight().background(p.divider))
                                SplitCell(row.rightNumber, row.rightText, row.rightType, row.intralineRight, numWidth, p, Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.width(contentWidth).fillMaxHeight()) {
                    items(unified.size, key = { it }) { i ->
                        val line = unified[i]
                        if (line.type == GitDiffLineType.HUNK) {
                            HunkRow(line.text, gutterWidth = numWidth * 2, p = p)
                        } else {
                            UnifiedRow(line, numWidth, p)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HunkRow(text: String, gutterWidth: androidx.compose.ui.unit.Dp, p: DiffPalette) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(p.hunkBg)) {
        Spacer(Modifier.width(gutterWidth).fillMaxHeight())
        Text(
            text = text,
            style = CodeStyle,
            color = p.hunkText,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = CodePad, vertical = 3.dp),
        )
    }
}

@Composable
private fun UnifiedRow(line: UniLine, numWidth: androidx.compose.ui.unit.Dp, p: DiffPalette) {
    val (lineBg, gutterBg, wordBg) = when (line.type) {
        GitDiffLineType.ADDED -> Triple(p.addLine, p.addGutter, p.addWord)
        GitDiffLineType.REMOVED -> Triple(p.delLine, p.delGutter, p.delWord)
        else -> Triple(Color.Transparent, Color.Transparent, Color.Transparent)
    }
    val sign = when (line.type) { GitDiffLineType.ADDED -> "+"; GitDiffLineType.REMOVED -> "\u2212"; else -> "" }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(lineBg)) {
        Gutter(line.oldNo, numWidth, gutterBg, p)
        Gutter(line.newNo, numWidth, gutterBg, p)
        Box(Modifier.width(SignWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(sign, style = CodeStyle, color = p.number)
        }
        CodeText(line.text, line.ranges, wordBg, Modifier.weight(1f).heightIn(min = 18.dp))
    }
}

@Composable
private fun SplitCell(
    number: Int?,
    text: String?,
    type: GitDiffLineType,
    ranges: List<IntRange>,
    numWidth: androidx.compose.ui.unit.Dp,
    p: DiffPalette,
    modifier: Modifier,
) {
    val (lineBg, gutterBg, wordBg) = when (type) {
        GitDiffLineType.ADDED -> Triple(p.addLine, p.addGutter, p.addWord)
        GitDiffLineType.REMOVED -> Triple(p.delLine, p.delGutter, p.delWord)
        GitDiffLineType.PADDING -> Triple(p.emptyBg, p.emptyBg, p.emptyBg)
        else -> Triple(Color.Transparent, Color.Transparent, Color.Transparent)
    }
    val sign = when (type) { GitDiffLineType.ADDED -> "+"; GitDiffLineType.REMOVED -> "\u2212"; else -> "" }
    Row(modifier.fillMaxHeight().background(lineBg)) {
        Gutter(number, numWidth, gutterBg, p)
        Box(Modifier.width(SignWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(sign, style = CodeStyle, color = p.number)
        }
        CodeText(text.orEmpty(), ranges, wordBg, Modifier.weight(1f).heightIn(min = 18.dp))
    }
}

@Composable
private fun Gutter(number: Int?, width: androidx.compose.ui.unit.Dp, bg: Color, p: DiffPalette) {
    Box(
        Modifier.width(width).fillMaxHeight().background(bg).padding(end = 10.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = number?.toString().orEmpty(),
            style = CodeStyle.copy(fontSize = 11.sp),
            color = p.number,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun CodeText(text: String, ranges: List<IntRange>, wordBg: Color, modifier: Modifier) {
    val annotated: AnnotatedString = remember(text, ranges, wordBg) {
        buildAnnotatedString {
            append(text)
            ranges.forEach { r ->
                val end = (r.last + 1).coerceIn(0, length)
                if (r.first < end) addStyle(SpanStyle(background = wordBg), r.first, end)
            }
        }
    }
    Text(
        text = annotated,
        style = CodeStyle,
        color = MaterialTheme.colorScheme.onSurface,
        softWrap = false,
        modifier = modifier.padding(horizontal = CodePad),
    )
}

/** Before/after for image files: HEAD blob bytes vs the worktree file. */
@Composable
private fun GitImageDiff(result: GitFileDiffResult) {
    val context = LocalContext.current
    val oldFile by produceState<File?>(initialValue = null, result.oldImageBytes) {
        val bytes = result.oldImageBytes ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                File.createTempFile("git_diff_old_", ".img", context.cacheDir).apply {
                    writeBytes(bytes)
                    deleteOnExit()
                }
            }.getOrNull()
        }
    }
    val newFile = result.workFilePath?.let { File(it) }
    Row(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.weight(1f).padding(4.dp)) {
            Text(stringResource(R.string.git_diff_before), style = MaterialTheme.typography.labelSmall)
            AsyncImage(model = oldFile, contentDescription = null, modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f).padding(4.dp)) {
            Text(stringResource(R.string.git_diff_after), style = MaterialTheme.typography.labelSmall)
            AsyncImage(model = newFile, contentDescription = null, modifier = Modifier.fillMaxWidth())
        }
    }
}
