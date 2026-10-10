package com.invictus.kodex.feature.permission

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.invictus.kodex.R
import com.invictus.kodex.ui.icons.XIcons

private val GrantedColor = Color(0xFF2E7D32)

/**
 * Startup permission onboarding: storage (mandatory) -> notifications (skippable),
 * styled after xmd's OnboardingScreen. Shown once,
 * before Home, gated by [com.invictus.kodex.core.permission.OnboardingPrefs].
 */
@Composable
fun OnboardingScreen(
    hasStoragePermission: Boolean,
    hasNotificationPermission: Boolean,
    onGrantStoragePermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onFinishOnboarding: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            // Step indicator: Storage, Notifications.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepDot(active = step == 0, completed = hasStoragePermission)
                StepConnector(filled = step > 0)
                StepDot(active = step == 1, completed = hasNotificationPermission)
            }

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "OnboardingStepTransition",
                ) { currentStep ->
                    when (currentStep) {
                        0 -> PermissionStepContent(
                            icon = XIcons.Folder,
                            granted = hasStoragePermission,
                            title = stringResource(R.string.startup_onboarding_title_storage),
                            description = stringResource(R.string.startup_onboarding_desc_storage),
                            grantedLabel = stringResource(R.string.startup_onboarding_storage_granted),
                            actionLabel = stringResource(R.string.startup_onboarding_grant_storage),
                            onAction = onGrantStoragePermission,
                        )
                        else -> PermissionStepContent(
                            icon = XIcons.Notifications,
                            granted = hasNotificationPermission,
                            title = stringResource(R.string.startup_onboarding_title_notification),
                            description = stringResource(R.string.startup_onboarding_desc_notification),
                            grantedLabel = stringResource(R.string.startup_onboarding_notification_granted),
                            actionLabel = stringResource(R.string.startup_onboarding_grant_notification),
                            hint = stringResource(R.string.startup_onboarding_notification_hint),
                            onAction = onRequestNotificationPermission,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step -= 1 }, shape = RoundedCornerShape(12.dp)) {
                        Icon(XIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.startup_onboarding_back))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                when (step) {
                    0 -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Button(
                                onClick = { step = 1 },
                                enabled = hasStoragePermission,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text(stringResource(R.string.startup_onboarding_next), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Icon(XIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            if (!hasStoragePermission) {
                                Text(
                                    text = stringResource(R.string.startup_onboarding_permission_required_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!hasNotificationPermission) {
                                OutlinedButton(onClick = onFinishOnboarding, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)) {
                                    Text(stringResource(R.string.startup_onboarding_skip))
                                }
                            }
                            Button(onClick = onFinishOnboarding, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)) {
                                Text(stringResource(R.string.startup_onboarding_get_started), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Icon(XIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDot(active: Boolean, completed: Boolean) {
    val backgroundColor = when {
        completed -> GrantedColor
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier.size(14.dp).clip(CircleShape).background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (completed) {
            Icon(XIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun StepConnector(filled: Boolean) {
    Spacer(Modifier.width(8.dp))
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(2.dp)
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    )
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, description: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionStepContent(
    icon: ImageVector,
    granted: Boolean,
    title: String,
    description: String,
    grantedLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
    hint: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepHeader(icon = icon, title = title, description = description)
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(
                width = 1.dp,
                color = if (granted) GrantedColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (granted) XIcons.Check else icon,
                        contentDescription = null,
                        tint = if (granted) GrantedColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (granted) grantedLabel else actionLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (granted) GrantedColor else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!granted) {
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

