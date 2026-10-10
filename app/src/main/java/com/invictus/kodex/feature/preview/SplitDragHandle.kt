package com.invictus.kodex.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.kodex.R
import com.invictus.kodex.ui.icons.XIcons

/**
 * The line between editor and preview in SPLIT mode. Touch target is taller than the visible
 * line so it's easy to grab on a phone; [onDragDeltaPx] hands back raw vertical drag so the
 * caller (which knows the container's total height) can turn it into a ratio.
 */
@Composable
fun SplitDragHandle(
    onDragDeltaPx: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDeltaPx(dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .padding(vertical = 1.dp)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
        )
        if (onClose != null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd).size(28.dp),
            ) {
                Icon(
                    XIcons.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.action_close_dialog),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
