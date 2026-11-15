package com.valghora.shrimpl.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valghora.shrimpl.BuildConfig
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.update.UpdateFrequency
import com.valghora.shrimpl.ui.common.DropdownItem
import com.valghora.shrimpl.ui.common.SettingsItem
import com.valghora.shrimpl.ui.common.SettingsSection
import com.valghora.shrimpl.ui.common.StatusButton
import com.valghora.shrimpl.ui.common.Tone
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
 * Shrimpl's version as the title, the latest check's state on one line in its tone, and one button
 * (R14.3): Check, Update once a release was found, Retry after a failure, a spinner while something
 * runs. The button keeps one size, so it doesn't move between states.
 */
@Composable
private fun VersionItem(status: UpdateStatus, onCheck: () -> Unit, onUpdate: () -> Unit) {
    val result = status.result
    val available = (result as? UpdateResult.Available)?.release
    val neutral = MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    val (state, tone) = when {
        status.work == UpdateWork.CHECKING -> stringResource(R.string.update_checking) to null
        status.work == UpdateWork.DOWNLOADING -> stringResource(R.string.update_downloading) to Tone.UPDATE
        status.work == UpdateWork.INSTALLING -> stringResource(R.string.update_installing) to Tone.UPDATE
        status.installError != null -> stringResource(R.string.update_install_failed) to Tone.ERROR
        result is UpdateResult.Available -> stringResource(R.string.update_available) to Tone.UPDATE
        result == UpdateResult.UpToDate -> stringResource(R.string.update_up_to_date) to Tone.SUCCESS
        result is UpdateResult.Failed -> stringResource(R.string.update_failed) to Tone.ERROR
        else -> stringResource(R.string.update_not_checked) to null
    }
    val check = stringResource(R.string.action_check)
    val update = stringResource(R.string.action_update)
    val retry = stringResource(R.string.action_retry)
    val (container, content) = tone?.let { it.container to it.content } ?: neutral
    SettingsItem(
        stringResource(R.string.update_current, BuildConfig.VERSION_NAME),
        summary = state,
        summaryColor = tone?.text,
        trailing = {
            StatusButton(
                label = when {
                    tone == Tone.ERROR -> retry
                    available != null -> update
                    else -> check
                },
                labels = listOf(check, update, retry),
                busy = status.work != null,
                busyDescription = state,
                container = container,
                content = content,
                // Retry after a failed update tries the update again; after a failed check, the check.
                onClick = if (available != null) onUpdate else onCheck,
            )
        },
    )
}

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
