package com.valghora.shrimpl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.valghora.shrimpl.R
import com.valghora.shrimpl.ui.TestApps.string
import com.valghora.shrimpl.ui.common.UninstallDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** docs/SHRIMPL_SPEC.md R2.2: the uninstall dialog's keep box starts unchecked and is one checkbox. */
@RunWith(RobolectricTestRunner::class)
class UninstallDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private var confirmed: Boolean? = null

    private fun show() = compose.setContent {
        MaterialTheme { UninstallDialog("WhatsApp", onConfirm = { confirmed = it }, onDismiss = {}) }
    }

    private val keepLabel get() = string(R.string.uninstall_keep)

    @Test
    fun uninstallingWithoutTheBoxRemovesTheApp() {
        show()
        compose.onNodeWithText(string(R.string.uninstall_text, "WhatsApp")).assertExists()
        compose.onNodeWithText(string(R.string.action_uninstall)).performClick()
        assertEquals(false, confirmed)
    }

    @Test
    fun theKeepBoxIsOneCheckboxThatKeepsTheApp() {
        show()
        val row = compose.onNodeWithText(keepLabel).fetchSemanticsNode().config
        assertEquals(Role.Checkbox, row[SemanticsProperties.Role])
        assertEquals(ToggleableState.Off, row[SemanticsProperties.ToggleableState])
        compose.onNodeWithText(keepLabel).performClick()
        compose.onNodeWithText(string(R.string.action_uninstall)).performClick()
        assertEquals(true, confirmed)
    }
}
