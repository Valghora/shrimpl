package com.valghora.shrimpl.webapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Web Share API (docs/TEMPLATE_SPEC.md T6.16) in the real WebView: `navigator.share` opens
 * Android's share sheet with the page's text and link, and its promise settles. Found missing by hand:
 * Share buttons did nothing in WebView.
 */
@RunWith(AndroidJUnit4::class)
class ShareBridgeTest {
    @get:Rule
    val intents = IntentsRule()

    private fun withPage(block: (PageDriver) -> Unit) {
        // Answer the chooser ourselves: the test checks what the template asks Android to share.
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> block(PageDriver(scenario).also { it.loadTestPage() }) }
    }

    /** The text intent inside the chooser the template started. */
    private fun sharedIntent(): Intent {
        val chooser = Intents.getIntents().first { it.action == Intent.ACTION_CHOOSER }
        return requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
    }

    @Test
    fun shareOpensTheShareSheetWithTextAndLink() = withPage { page ->
        val result = page.runAsync(
            """
            await navigator.share({ title: 'Board', text: 'Look at this', url: '/board/1' });
            window.__r = 'shared';
            """,
        )
        assertEquals("shared", result)
        val send = sharedIntent()
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        // The link is made absolute against the page and goes on its own line after the text.
        assertEquals("Look at this\nhttps://example.com/board/1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Board", send.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun canShareSaysYesToTextAndNoToFiles() = withPage { page ->
        assertEquals("true", page.js("navigator.canShare({ text: 'hi' })"))
        assertEquals("false", page.js("navigator.canShare({ files: [new File(['x'], 'x.txt')] })"))
        assertEquals("false", page.js("navigator.canShare({})"))
    }

    @Test
    fun sharingFilesOrNothingIsRefusedLikeABrowserDoes() = withPage { page ->
        assertEquals(
            "error:NotAllowedError:Sharing files is not supported",
            page.runAsync("await navigator.share({ files: [new File(['x'], 'x.txt')] }); window.__r = 'shared';"),
        )
        assertEquals("error:TypeError:Nothing to share", page.runAsync("await navigator.share({}); window.__r = 'shared';"))
    }
}
