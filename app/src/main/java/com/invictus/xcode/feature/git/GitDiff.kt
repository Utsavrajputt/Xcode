package com.invictus.xcode.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.invictus.xcode.R
import com.invictus.xcode.core.git.model.GitDiffLineType
import com.invictus.xcode.core.git.model.GitFileDiffResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Diff viewer (M7): inline / side-by-side toggle, intraline highlight, binary notice,
 * and an old-vs-new image comparison for image files.
 */
@Composable
fun GitDiffViewer(result: GitFileDiffResult, modifier: Modifier = Modifier) {
    var sideBySide by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            FilterChip(
                selected = !sideBySide,
                onClick = { sideBySide = false },
                label = { Text(stringResource(R.string.git_diff_inline)) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = sideBySide,
                onClick = { sideBySide = true },
                label = { Text(stringResource(R.string.git_diff_side_by_side)) },
            )
        }
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
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DiffRows(result: GitFileDiffResult, sideBySide: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val addBg = remember(scheme) { scheme.primary.copy(alpha = 0.10f) }
    val delBg = remember(scheme) { scheme.error.copy(alpha = 0.10f) }
    val addHl = remember(scheme) { scheme.primary.copy(alpha = 0.30f) }
    val delHl = remember(scheme) { scheme.error.copy(alpha = 0.30f) }
    val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(result.rows, key = { i, _ -> i }) { _, row ->
            if (sideBySide) {
                Row(Modifier.fillMaxWidth()) {
                    DiffCell(
                        number = row.leftNumber, text = row.leftText.orEmpty(), type = row.leftType,
                        ranges = row.intralineLeft, bg = delBg, highlight = delHl,
                        gutterColor = gutterColor, modifier = Modifier.weight(1f),
                    )
                    VerticalDivider()
                    DiffCell(
                        number = row.rightNumber, text = row.rightText.orEmpty(), type = row.rightType,
                        ranges = row.intralineRight, bg = addBg, highlight = addHl,
                        gutterColor = gutterColor, modifier = Modifier.weight(1f),
                    )
                }
            } else {
                val isLeft = row.leftType == GitDiffLineType.REMOVED
                DiffCell(
                    number = row.leftNumber ?: row.rightNumber,
                    text = row.leftText ?: row.rightText.orEmpty(),
                    type = if (isLeft) row.leftType else row.rightType,
                    ranges = if (isLeft) row.intralineLeft else row.intralineRight,
                    bg = if (isLeft) delBg else addBg,
                    highlight = if (isLeft) delHl else addHl,
                    gutterColor = gutterColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DiffCell(
    number: Int?,
    text: String,
    type: GitDiffLineType,
    ranges: List<IntRange>,
    bg: Color,
    highlight: Color,
    gutterColor: Color,
    modifier: Modifier = Modifier,
) {
    val background = when (type) {
        GitDiffLineType.ADDED, GitDiffLineType.REMOVED -> bg
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .background(background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = number?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = gutterColor,
            modifier = Modifier.width(36.dp),
        )
        DiffText(text = text, ranges = ranges, highlight = highlight)
    }
}

@Composable
private fun DiffText(text: String, ranges: List<IntRange>, highlight: Color) {
    val annotated: AnnotatedString = remember(text, ranges, highlight) {
        buildAnnotatedString {
            append(text)
            ranges.forEach { r ->
                val end = (r.last + 1).coerceIn(0, length)
                if (r.first < end) addStyle(SpanStyle(background = highlight), r.first, end)
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        softWrap = false,
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
