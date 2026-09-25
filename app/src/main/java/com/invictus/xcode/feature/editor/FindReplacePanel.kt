package com.invictus.xcode.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.ui.icons.XIcons
import java.util.regex.PatternSyntaxException

/**
 * Transient find/replace UI state for one editor visit. Not persisted (plan 3.2, M4): closing
 * the panel or switching tabs just drops it, same as ACSIDE.
 */
class FindReplaceState {
    var query: String by mutableStateOf("")
    var replacement: String by mutableStateOf("")
    var caseSensitive: Boolean by mutableStateOf(false)
    var useRegex: Boolean by mutableStateOf(false)
    var replaceExpanded: Boolean by mutableStateOf(false)

    /** Count of matches in the active tab's current text, or null while [query] is an invalid regex. */
    var matchCount: Int? by mutableStateOf<Int?>(0)

    /** Bumped by the editor's edit callback so a recompute runs while the panel is open. */
    var editEpoch: Int by mutableIntStateOf(0)

    fun reset() {
        query = ""
        replacement = ""
        matchCount = 0
    }
}

/**
 * Counts matches independently of Sora's own searcher (which only exposes navigation, not a
 * stable count) so the panel can show "12 matches" / a regex-error state without depending on
 * [io.github.rosemoe.sora.widget.EditorSearcher] internals that aren't part of its stable API.
 */
fun countMatches(text: CharSequence, query: String, caseSensitive: Boolean, useRegex: Boolean): Int? {
    if (query.isEmpty()) return 0
    val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    return try {
        val pattern = if (useRegex) Regex(query, options) else Regex(Regex.escape(query), options)
        var count = 0
        var start = 0
        while (start <= text.length) {
            val match = pattern.find(text, start) ?: break
            count++
            start = if (match.range.isEmpty()) match.range.first + 1 else match.range.last + 1
        }
        count
    } catch (_: PatternSyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Composable
fun FindReplacePanel(
    state: FindReplaceState,
    onQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val matchCount = state.matchCount
    val hasMatches = (matchCount ?: 0) > 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.find_hint)) },
                    isError = matchCount == null,
                    trailingIcon = {
                        Text(
                            text = when {
                                state.query.isEmpty() -> ""
                                matchCount == null -> stringResource(R.string.find_invalid_regex)
                                else -> matchCount.toString()
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    },
                )
                IconButton(onClick = onFindPrevious, enabled = hasMatches) {
                    Icon(XIcons.KeyboardArrowUp, contentDescription = stringResource(R.string.find_previous))
                }
                IconButton(onClick = onFindNext, enabled = hasMatches) {
                    Icon(XIcons.KeyboardArrowDown, contentDescription = stringResource(R.string.find_next))
                }
                IconButton(onClick = onClose) {
                    Icon(XIcons.Close, contentDescription = stringResource(R.string.find_close))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                FilterChip(
                    selected = state.caseSensitive,
                    onClick = { state.caseSensitive = !state.caseSensitive },
                    label = { Text(stringResource(R.string.find_case_sensitive)) },
                    modifier = Modifier.widthIn(min = 0.dp),
                )
                FilterChip(
                    selected = state.useRegex,
                    onClick = { state.useRegex = !state.useRegex },
                    label = { Text(stringResource(R.string.find_use_regex)) },
                    modifier = Modifier.padding(start = 6.dp),
                )
                FilterChip(
                    selected = state.replaceExpanded,
                    onClick = { state.replaceExpanded = !state.replaceExpanded },
                    label = { Text(stringResource(R.string.find_replace)) },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (state.replaceExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    OutlinedTextField(
                        value = state.replacement,
                        onValueChange = { state.replacement = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.find_replace_hint)) },
                    )
                    TextButton(onClick = onReplace, enabled = hasMatches) {
                        Text(stringResource(R.string.find_replace_one))
                    }
                    TextButton(onClick = onReplaceAll, enabled = hasMatches) {
                        Text(stringResource(R.string.find_replace_all))
                    }
                }
            }
        }
    }
}
