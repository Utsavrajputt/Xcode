package com.invictus.xcode.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.core.git.ConflictParser
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * M10 block-level conflicts, v1 (plan option (a)): a slim bar above the editor lists the
 * conflict blocks in the active file and jumps between them; "Resolve" opens a bottom
 * sheet with Accept ours / theirs / both. Editing a block by hand is always possible;
 * staging stays the drawer's job.
 */
@Composable
internal fun ConflictBlocksBar(
    blocks: List<ConflictParser.Block>,
    handle: EditorHandle,
    onEdited: () -> Unit,
) {
    if (blocks.isEmpty()) return
    var current by rememberSaveable { mutableIntStateOf(0) }
    current = current.coerceIn(0, blocks.lastIndex)
    var sheetBlock by rememberSaveable(blocks.size) { mutableIntStateOf(-1) }

    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Text(
                stringResource(R.string.conflict_blocks_count, blocks.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = {
                current = (current - 1 + blocks.size) % blocks.size
                jumpToBlock(handle.editor, blocks[current])
            }) { Text("‹") }
            Text(
                "${'$'}{current + 1}/${'$'}{blocks.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = {
                current = (current + 1) % blocks.size
                jumpToBlock(handle.editor, blocks[current])
            }) { Text("›") }
            TextButton(onClick = { sheetBlock = current }) {
                Text(stringResource(R.string.conflict_resolve))
            }
        }
    }

    if (sheetBlock in blocks.indices) {
        ConflictBlockSheet(
            block = blocks[sheetBlock],
            onChoice = { choice ->
                applyConflictChoice(handle.editor, blocks[sheetBlock], choice)
                onEdited()
                sheetBlock = -1
            },
            onDismiss = { sheetBlock = -1 },
        )
    }
}

private fun jumpToBlock(editor: CodeEditor?, block: ConflictParser.Block) {
    runCatching { editor?.setSelection(block.startLine, 0) }
}

/** Replaces the marker region with the chosen side; fires ContentChangeEvent -> dirty. */
internal fun applyConflictChoice(
    editor: CodeEditor?,
    block: ConflictParser.Block,
    choice: ConflictParser.Choice,
) {
    editor ?: return
    runCatching {
        val content = editor.text
        val start = content.getCharIndex(block.startLine, 0)
        val end = content.getCharIndex(
            block.endLine,
            content.getColumnCount(block.endLine),
        )
        content.delete(start, end)
        val repl = block.replacement(choice)
        if (repl.isNotEmpty()) content.insert(block.startLine, 0, repl)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictBlockSheet(
    block: ConflictParser.Block,
    onChoice: (ConflictParser.Choice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.conflict_block_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.conflict_ours_label, block.labelOurs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                block.oursText.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.conflict_theirs_label, block.labelTheirs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                block.theirsText.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onChoice(ConflictParser.Choice.OURS) }) {
                    Text(stringResource(R.string.conflict_accept_ours))
                }
                Button(onClick = { onChoice(ConflictParser.Choice.THEIRS) }) {
                    Text(stringResource(R.string.conflict_accept_theirs))
                }
                TextButton(onClick = { onChoice(ConflictParser.Choice.BOTH) }) {
                    Text(stringResource(R.string.conflict_accept_both))
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}
