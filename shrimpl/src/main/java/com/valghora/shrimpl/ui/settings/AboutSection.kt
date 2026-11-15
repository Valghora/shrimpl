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
import com.valghora.shrimpl.ui.theme.AppShapes
import com.valghora.shrimpl.update.UpdateResult
import com.valghora.shrimpl.update.UpdateStatus
import com.valghora.shrimpl.update.UpdateWork

/**
 * The About section of Settings (docs/SHRIMPL_SPEC.md R14.3): Shrimpl's version, with the latest
 * update check and Check or Update, then Check for updates, how often the background check runs or
 * Disabled. Choosing an interval or checking by hand asks for the notification permission the update
 * notification needs.
 */
@Composable
fun AboutSection(model: SettingsViewModel) {
    val frequency by model.updateFrequency.collectAsStateWithLifecycle()
    val status by model.updateStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askForNotifications = {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    SettingsSection(stringResource(R.string.settings_about)) {
        VersionItem(
            status,
            onCheck = {
                askForNotifications()
                model.checkNow()
            },
            onUpdate = model::installUpdate,
        )
        DropdownItem(
            stringResource(R.string.update_check),
            frequency,
            UpdateFrequency.entries.map { it to stringResource(it.label()) },
            { chosen ->
                model.setUpdateFrequency(chosen)
                if (chosen != UpdateFrequency.DISABLED) askForNotifications()
            },
            summary = stringResource(frequency.detail()),
        )
    }
}

/**
 * Shrimpl's version as the title; the latest check's result, then when it ran, each on its own line;
 * and Check, or Update once a release was found (R14.3).
 */
@Composable
private fun VersionItem(status: UpdateStatus, onCheck: () -> Unit, onUpdate: () -> Unit) {
    val context = LocalContext.current
    val result = status.result
    val available = (result as? UpdateResult.Available)?.release
    val state = when (status.work) {
        UpdateWork.CHECKING -> stringResource(R.string.update_checking)
        UpdateWork.DOWNLOADING -> stringResource(R.string.update_downloading)
        UpdateWork.INSTALLING -> stringResource(R.string.update_installing)
        null -> when (result) {
            is UpdateResult.Available -> stringResource(R.string.update_available, result.release.version)
            UpdateResult.UpToDate -> stringResource(R.string.update_up_to_date)
            is UpdateResult.Failed -> stringResource(R.string.update_failed, result.reason)
            null -> stringResource(R.string.update_not_checked)
        }
    }
    val checked = status.lastChecked?.let { stringResource(R.string.update_last_checked, relativeTime(context, it)) }
    SettingsItem(
        stringResource(R.string.update_current, BuildConfig.VERSION_NAME),
        summary = listOfNotNull(state, checked).joinToString("\n"),
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
                    Text(stringResource(if (available != null) R.string.action_update else R.string.action_check))
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
    UpdateFrequency.DISABLED -> R.string.update_frequency_disabled
    UpdateFrequency.HOUR -> R.string.update_frequency_hour
    UpdateFrequency.SIX_HOURS -> R.string.update_frequency_six_hours
    UpdateFrequency.TWELVE_HOURS -> R.string.update_frequency_twelve_hours
    UpdateFrequency.DAY -> R.string.update_frequency_day
    UpdateFrequency.WEEK -> R.string.update_frequency_week
}

private fun UpdateFrequency.detail() = when (this) {
    UpdateFrequency.DISABLED -> R.string.update_frequency_disabled_detail
    UpdateFrequency.HOUR -> R.string.update_frequency_hour_detail
    UpdateFrequency.SIX_HOURS -> R.string.update_frequency_six_hours_detail
    UpdateFrequency.TWELVE_HOURS -> R.string.update_frequency_twelve_hours_detail
    UpdateFrequency.DAY -> R.string.update_frequency_day_detail
    UpdateFrequency.WEEK -> R.string.update_frequency_week_detail
}
