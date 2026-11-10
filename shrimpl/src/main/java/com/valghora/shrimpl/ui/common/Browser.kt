package com.valghora.shrimpl.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.valghora.shrimpl.R

/**
 * Opens a site outside Shrimpl's web apps, for one that needs a Google sign-in (R3.3): Google refuses
 * sign-in in WebViews, while Chrome can sign in and install the site as an app.
 */
object Browser {
    /** Chrome's package; the manifest's queries make it visible. */
    private const val CHROME = "com.android.chrome"

    private fun view(address: String) = Intent(Intent.ACTION_VIEW, address.toUri())

    /** Whether Chrome is installed and enabled, so the notice can name it. */
    fun hasChrome(context: Context): Boolean =
        context.packageManager.resolveActivity(view("https://example.com/").setPackage(CHROME), 0) != null

    /** Opens [address] in Chrome when [chrome] is true, otherwise in the default browser; says so when there is none. */
    fun open(context: Context, address: String, chrome: Boolean) {
        val intent = view(address).addCategory(Intent.CATEGORY_BROWSABLE)
        if (chrome) intent.setPackage(CHROME)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
