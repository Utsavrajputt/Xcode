package com.invictus.xcode.feature.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.invictus.xcode.R

/** M1 placeholder home. The real file tree + recent projects land in M2. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.workspace_title)) }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.workspace_placeholder))
        }
    }
}
