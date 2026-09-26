package com.invictus.xcode.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.invictus.xcode.R
import com.invictus.xcode.core.preview.PreviewRouter
import com.invictus.xcode.core.preview.PreviewType
import com.invictus.xcode.ui.icons.XIcons
import java.io.File

/** SVG/image/video open straight here from the file tree -- see EditorViewModel.openMediaPreview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewScreen(file: File, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (PreviewRouter.typeOf(file)) {
                PreviewType.SVG -> SvgPreviewView(file, modifier = Modifier.fillMaxSize())
                PreviewType.IMAGE -> ImagePreviewView(file, modifier = Modifier.fillMaxSize())
                PreviewType.VIDEO -> VideoPreviewView(file, modifier = Modifier.fillMaxSize())
                else -> Text(
                    stringResource(R.string.preview_unsupported),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
