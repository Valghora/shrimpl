package com.valghora.uniwrap.build

import android.content.Context
import androidx.core.graphics.toColorInt
import com.valghora.uniwrap.data.SigningKeyStore
import com.valghora.uniwrap.data.TemplateProvider
import com.valghora.uniwrap.data.WebAppStore
import com.valghora.uniwrap.icons.IconRenderer
import com.valghora.uniwrap.install.InstallResult
import com.valghora.uniwrap.install.Installer
import com.valghora.wrapper.core.build.BuildCancelledException
import com.valghora.wrapper.core.build.BuildException
import com.valghora.wrapper.core.build.WebAppBuilder
import com.valghora.wrapper.core.build.WebAppSpec
import com.valghora.wrapper.core.icons.IconException
import com.valghora.wrapper.core.profile.Profile
import com.valghora.wrapper.core.profile.resolvedIconForeground
import com.valghora.wrapper.core.records.IconRecord
import com.valghora.wrapper.core.records.WebAppRecord
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** What to build: settings, icon, the icon's image (when new or changed), and the existing record. */
class BuildRequest(
    val profile: Profile,
    val icon: IconRecord,
    /** New image bytes for an image icon; null keeps the stored image. */
    val newImage: ByteArray?,
    val existing: WebAppRecord?,
)

sealed interface BuildOutcome {
    data class Installed(val record: WebAppRecord) : BuildOutcome

    data object NeedsUnknownSources : BuildOutcome

    data class Failed(val message: String, val installMethodUnavailable: Boolean = false) : BuildOutcome
}

/**
 * Makes and installs a web app (docs/UNIWRAP_SPEC.md R8, R6): renders the icon, builds and signs from
 * the template, installs, and only then saves the record, so a failed install never uses up a build
 * number (R11.3).
 */
class WebAppFactory(
    private val context: Context,
    private val store: WebAppStore,
    private val keys: SigningKeyStore,
    private val template: TemplateProvider,
) {
    enum class Stage { RENDERING_ICON, BUILDING, SIGNING, VERIFYING, INSTALLING }

    suspend fun build(request: BuildRequest, installer: Installer, onStage: (Stage) -> Unit): BuildOutcome {
        val profile = request.profile
        val buildNumber = request.existing?.nextBuildNumber ?: WebAppRecord.FIRST_BUILD
        val output = File(context.cacheDir, "builds/${profile.appId}-$buildNumber.apk")
        try {
            onStage(Stage.RENDERING_ICON)
            val icons = withContext(Dispatchers.Default) {
                val source = request.icon.toSource { request.newImage ?: store.image(profile.appId) }
                IconRenderer.render(source, profile.iconBackground.toColorInt(), profile.resolvedIconForeground.toColorInt(), letterOf(profile.name))
            }

            onStage(Stage.BUILDING)
            val active = coroutineContext
            val built = withContext(Dispatchers.IO) {
                output.parentFile?.mkdirs()
                val key = keys.getOrCreate()
                WebAppBuilder.build(
                    template = template.file(),
                    output = output,
                    spec = WebAppSpec(profile, buildNumber, icons),
                    key = key,
                    onStage = { stage ->
                        when (stage) {
                            WebAppBuilder.Stage.PATCHING -> Unit
                            WebAppBuilder.Stage.SIGNING -> onStage(Stage.SIGNING)
                            WebAppBuilder.Stage.VERIFYING -> onStage(Stage.VERIFYING)
                        }
                    },
                    isCancelled = { !active.isActive },
                )
            }

            onStage(Stage.INSTALLING)
            return when (val result = installer.install(built.apk)) {
                InstallResult.Success -> {
                    val now = System.currentTimeMillis()
                    val record = request.existing?.installed(profile, request.icon, buildNumber, template.version, built.signerFingerprint, now)
                        ?: WebAppRecord.create(profile, request.icon, template.version, built.signerFingerprint, now)
                    store.save(record, request.newImage)
                    BuildOutcome.Installed(record)
                }
                InstallResult.NeedsUnknownSources -> BuildOutcome.NeedsUnknownSources
                is InstallResult.Failure -> BuildOutcome.Failed(result.message, result.methodUnavailable)
            }
        } catch (e: BuildCancelledException) {
            throw kotlinx.coroutines.CancellationException(e.message)
        } catch (e: BuildException) {
            return BuildOutcome.Failed(e.message ?: "The build failed")
        } catch (e: IconException) {
            return BuildOutcome.Failed(e.message ?: "The icon could not be drawn")
        } catch (e: java.io.IOException) {
            return BuildOutcome.Failed(e.message ?: "Reading or writing failed")
        } catch (e: java.security.GeneralSecurityException) {
            return BuildOutcome.Failed("Signing failed: ${e.message}")
        } catch (e: com.valghora.wrapper.core.build.SigningKeyException) {
            return BuildOutcome.Failed(e.message ?: "The signing key is unusable")
        } finally {
            output.delete()
        }
    }

    companion object {
        /** The letter used for themed and notification icons of image icons. */
        fun letterOf(name: String): Char = name.uppercase().firstOrNull { it.code < 128 && it.isLetterOrDigit() } ?: 'A'
    }
}
