package com.valghora.shrimpl.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valghora.shrimpl.BuildConfig
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.update.UpdateFrequency
import com.valghora.shrimpl.ui.common.DropdownItem
import com.valghora.shrimpl.ui.common.SettingsItem
import com.valghora.shrimpl.ui.common.SettingsSection
import com.valghora.shrimpl.ui.common.SwitchItem
import com.valghora.shrimpl.ui.theme.AppShapes
import com.valghora.shrimpl.update.UpdateResult
import com.valghora.shrimpl.update.UpdateStatus
import com.valghora.shrimpl.update.UpdateWork

/**
 * The Updates section of Settings (docs/SHRIMPL_SPEC.md R14.3): Check for updates, Check frequency
 * (off while the switch is), and the latest check's result with Check now or Update. Turning the
 * switch on or checking by hand asks for the notification permission the update notification needs.
 */
@Composable
fun UpdateSettings(model: SettingsViewModel) {
    val enabled by model.checkForUpdates.collectAsStateWithLifecycle()
    val frequency by model.updateFrequency.collectAsStateWithLifecycle()
    val status by model.updateStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askForNotifications = {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    SettingsSection(stringResource(R.string.settings_updates)) {
        SwitchItem(
            stringResource(R.string.update_check),
            enabled,
            { on ->
                model.setCheckForUpdates(on)
                if (on) askForNotifications()
            },
            summary = stringResource(R.string.update_check_detail),
        )
        DropdownItem(
            stringResource(R.string.update_frequency),
            frequency,
            UpdateFrequency.entries.map { it to stringResource(it.label()) },
            model::setUpdateFrequency,
            summary = stringResource(frequency.detail()),
            enabled = enabled,
        )
        UpdateStatusItem(
            status,
            onCheck = {
                askForNotifications()
                model.checkNow()
            },
            onUpdate = model::installUpdate,
        )
    }
}

/** The latest check's result, its line of detail and when it ran, each on its own line (R14.3). */
@Composable
private fun UpdateStatusItem(status: UpdateStatus, onCheck: () -> Unit, onUpdate: () -> Unit) {
    val context = LocalContext.current
    val result = status.result
    val available = (result as? UpdateResult.Available)?.release
    val title = when {
        available != null -> stringResource(R.string.update_available, available.version)
        result is UpdateResult.Failed -> stringResource(R.string.update_failed)
        else -> stringResource(R.string.update_current, BuildConfig.VERSION_NAME)
    }
    val detail = when (status.work) {
        UpdateWork.CHECKING -> stringResource(R.string.update_checking)
        UpdateWork.DOWNLOADING -> stringResource(R.string.update_downloading)
        UpdateWork.INSTALLING -> stringResource(R.string.update_installing)
        null -> when (result) {
            is UpdateResult.Available -> stringResource(R.string.update_ready)
            UpdateResult.UpToDate -> stringResource(R.string.update_up_to_date)
            is UpdateResult.Failed -> result.reason
            null -> null
        }
    }
    val checked = status.lastChecked?.let { stringResource(R.string.update_last_checked, relativeTime(context, it)) }
        ?: stringResource(R.string.update_not_checked)
    SettingsItem(
        title,
        summary = listOfNotNull(detail, checked).joinToString("\n"),
        error = status.installError,
        trailing = {
            Button(
                onClick = if (available != null) onUpdate else onCheck,
                enabled = status.work == null,
                shape = AppShapes.Control,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                if (status.work != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (available != null) R.string.action_update else R.string.action_check_now))
                }
            }
        },
    )
}

/** "2 hours ago", as Android words it in the user's language. */
private fun relativeTime(context: Context, time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        .replaceFirstChar { it.lowercase(context.resources.configuration.locales[0]) }

private fun UpdateFrequency.label() = when (this) {
    UpdateFrequency.HOUR -> R.string.update_frequency_hour
    UpdateFrequency.SIX_HOURS -> R.string.update_frequency_six_hours
    UpdateFrequency.TWELVE_HOURS -> R.string.update_frequency_twelve_hours
    UpdateFrequency.DAY -> R.string.update_frequency_day
    UpdateFrequency.WEEK -> R.string.update_frequency_week
}

private fun UpdateFrequency.detail() = when (this) {
    UpdateFrequency.HOUR -> R.string.update_frequency_hour_detail
    UpdateFrequency.SIX_HOURS -> R.string.update_frequency_six_hours_detail
    UpdateFrequency.TWELVE_HOURS -> R.string.update_frequency_twelve_hours_detail
    UpdateFrequency.DAY -> R.string.update_frequency_day_detail
    UpdateFrequency.WEEK -> R.string.update_frequency_week_detail
}
