package com.invictus.kodex.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.invictus.kodex.core.fs.TreeUriPath

/**
 * Launches the system folder picker (`ACTION_OPEN_DOCUMENT_TREE`) and resolves the chosen tree
 * to a raw filesystem path via [TreeUriPath]. [onPicked] only fires when a path could be
 * resolved on the primary storage volume; picks that can't be resolved are silently ignored,
 * same as a cancelled picker.
 */
@Composable
fun rememberFolderPickerLauncher(
    onPicked: (String) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        TreeUriPath.resolve(uri)?.let(onPicked)
    }
    return remember(launcher) {
        { launcher.launch(null) }
    }
}
