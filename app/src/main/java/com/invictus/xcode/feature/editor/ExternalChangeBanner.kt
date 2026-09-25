package com.invictus.xcode.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.core.editor.ExternalChange

/**
 * Sits between the tab bar and the editor for the active tab only (plan 7): tells the user its
 * file changed underneath the app and lets them choose what happens next, rather than silently
 * clobbering either side. A clean (non-dirty) tab never shows this -- [EditorViewModel] already
 * reloaded it silently before the banner would ever appear.
 */
@Composable
fun ExternalChangeBanner(tab: EditorTabUi, onEvent: (EditorEvent) -> Unit) {
    val deleted = tab.externalChange == ExternalChange.Deleted
    // Expressive spring-driven show/hide instead of an abrupt if-return.
    AnimatedVisibility(
        visible = tab.externalChange != ExternalChange.None,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(),
        exit = shrinkVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeOut(),
    ) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (deleted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (deleted) R.string.editor_external_deleted else R.string.editor_external_modified,
                    tab.name,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = if (deleted) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
            if (deleted) {
                TextButton(onClick = { onEvent(EditorEvent.CloseDeletedTab(tab.path)) }) {
                    Text(stringResource(R.string.editor_external_close_tab))
                }
                TextButton(onClick = { onEvent(EditorEvent.KeepAsNewFile(tab.path)) }) {
                    Text(stringResource(R.string.editor_external_keep_as_new))
                }
            } else {
                TextButton(onClick = { onEvent(EditorEvent.KeepMyEdits(tab.path)) }) {
                    Text(stringResource(R.string.editor_external_keep_edits))
                }
                TextButton(onClick = { onEvent(EditorEvent.ReloadFromDisk(tab.path)) }) {
                    Text(stringResource(R.string.editor_external_reload))
                }
            }
        }
    }
    }
}
