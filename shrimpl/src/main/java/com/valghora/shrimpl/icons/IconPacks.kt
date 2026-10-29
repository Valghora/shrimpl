package com.valghora.shrimpl.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** An installed icon pack (docs/SHRIMPL_SPEC.md R10.2). */
data class IconPack(val packageName: String, val label: String)

/**
 * Reads icon packs in the common launcher format: apps that answer the ADW or Nova theme intents and
 * list their icons in `appfilter.xml` (and often `drawable.xml`).
 */
object IconPacks {
    private val THEME_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")

    fun installed(context: Context): List<IconPack> {
        val pm = context.packageManager
        return THEME_ACTIONS.flatMap { action ->
            pm.queryIntentActivities(Intent(action), PackageManager.ResolveInfoFlags.of(0))
        }
            .map { it.activityInfo.packageName }
            .distinct()
            .map { pkg -> IconPack(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** The pack's drawable names, from appfilter.xml and drawable.xml; [suggestFor] matches come first. */
    fun icons(context: Context, pack: IconPack, suggestFor: String): List<String> {
        val res = resources(context, pack) ?: return emptyList()
        val names = (drawables(res, pack, "appfilter", "drawable") + drawables(res, pack, "drawable", "drawable")).distinct()
        val key = suggestFor.lowercase().filter(Char::isLetterOrDigit)
        return if (key.isEmpty()) names else names.sortedByDescending { name -> name.filter(Char::isLetterOrDigit).contains(key) }
    }

    /** The icon as a square PNG of [sizePx], or null if the pack doesn't have it. */
    fun render(context: Context, pack: IconPack, name: String, sizePx: Int = 512): ByteArray? {
        val res = resources(context, pack) ?: return null
        val drawable = ResourcesCompat.getDrawable(res, identifier(res, name, "drawable", pack.packageName).takeIf { it != 0 } ?: return null, null)
            ?: return null
        val bitmap = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    private fun resources(context: Context, pack: IconPack): Resources? = try {
        context.packageManager.getResourcesForApplication(pack.packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Values of the `drawable` attribute in the pack's res/xml/[file].xml. */
    private fun drawables(res: Resources, pack: IconPack, file: String, attribute: String): List<String> {
        val id = identifier(res, file, "xml", pack.packageName)
        if (id == 0) return emptyList()
        val names = mutableListOf<String>()
        try {
            res.getXml(id).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        parser.getAttributeValue(null, attribute)?.takeIf(String::isNotBlank)?.let(names::add)
                    }
                }
            }
        } catch (_: XmlPullParserException) {
            // A broken list still yields what was read before the error.
        } catch (_: IOException) {
        }
        return names
    }

    /** Icon packs are looked up by name: their resource ids are only known at runtime (lint: DiscouragedApi, accepted). */
    private fun identifier(res: Resources, name: String, type: String, packageName: String) = res.getIdentifier(name, type, packageName)
}
