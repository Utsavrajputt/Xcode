package com.invictus.xcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.xcode.ui.icons.XIcons

/** Folder glyph for directories, coloured extension badge for files. */
@Composable
fun FileTypeIcon(
    name: String,
    isDirectory: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    if (isDirectory) {
        Icon(
            imageVector = XIcons.Folder,
            contentDescription = null,
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.primary,
        )
        return
    }
    val style = remember(name) { FileTypeConfig.styleFor(name) }
    Box(
        modifier = modifier
            .size(size)
            .background(style.background, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = style.label,
            color = style.foreground,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
