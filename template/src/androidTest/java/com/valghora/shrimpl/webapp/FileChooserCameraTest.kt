package com.valghora.shrimpl.webapp

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * The file chooser's camera options (docs/TEMPLATE_SPEC.md T6.4), opened by a real tap on a file input:
 * WebView shows a chooser only after a user gesture, so a script can't open it. Every launched activity
 * is answered with Cancel, so the test checks what the template asked Android for.
 *
 * The test template declares the camera permission (its manifest is the superset, R7.3), so the camera
 * is offered only once the permission is granted. The first test runs without it; the others grant it,
 * and a granted permission can't be taken back without ending the test process, hence the fixed order.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FileChooserCameraTest {
    @get:Rule
    val intents = IntentsRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun cancelEveryActivity() {
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    private fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun grantCamera() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
    }

    /** Taps a file input with [attributes] and returns the activity the template launched for it. */
    private fun chooserFor(attributes: String): Intent = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        val page = PageDriver(scenario).also {
            it.loadTestPage("<input id=\"pick\" type=\"file\" $attributes style=\"display:block;width:100%;height:240px\">")
        }
        page.tap("#pick")
        awaitUntil("the template to open a chooser for $attributes", 15_000) { Intents.getIntents().isNotEmpty() }
        Intents.getIntents().first()
    }

    private fun Intent.assertCamera(action: String) {
        assertEquals(action, this.action)
        val output = requireNotNull(getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)) { "$action has no EXTRA_OUTPUT" }
        assertEquals("content", output.scheme)
        assertEquals("${context.packageName}.files", output.authority)
    }

    @Test
    fun a_withoutTheCameraPermissionAPhotoInputOffersFilesOnly() {
        assumeFalse("an earlier run left the camera permission granted", cameraGranted())
        val launched = chooserFor("accept=\"image/*\"")
        assertNotEquals(Intent.ACTION_CHOOSER, launched.action)
        assertNotEquals(MediaStore.ACTION_IMAGE_CAPTURE, launched.action)
    }

    @Test
    fun b_aPhotoInputOffersTheCameraBesideTheFiles() {
        grantCamera()
        val launched = chooserFor("accept=\"image/*\"")
        assertEquals(Intent.ACTION_CHOOSER, launched.action)
        val picker = requireNotNull(launched.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)) { "the chooser has no picker" }
        assertNotEquals(MediaStore.ACTION_IMAGE_CAPTURE, picker.action)
        val cameras = launched.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS, Intent::class.java).orEmpty()
        assertEquals(listOf(MediaStore.ACTION_IMAGE_CAPTURE), cameras.map { it.action })
        cameras.single().assertCamera(MediaStore.ACTION_IMAGE_CAPTURE)
    }

    @Test
    fun c_aVideoInputOffersTheVideoCamera() {
        grantCamera()
        val launched = chooserFor("accept=\"video/*\"")
        assertEquals(Intent.ACTION_CHOOSER, launched.action)
        val cameras = launched.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS, Intent::class.java).orEmpty()
        assertEquals(listOf(MediaStore.ACTION_VIDEO_CAPTURE), cameras.map { it.action })
        cameras.single().assertCamera(MediaStore.ACTION_VIDEO_CAPTURE)
    }

    @Test
    fun d_aCaptureInputOpensTheCameraOnItsOwn() {
        grantCamera()
        chooserFor("accept=\"image/*\" capture=\"environment\"").assertCamera(MediaStore.ACTION_IMAGE_CAPTURE)
    }
}
