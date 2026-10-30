package com.valghora.shrimpl.build

import android.content.Context
import androidx.core.graphics.toColorInt
import com.valghora.shrimpl.data.SigningKeyStore
import com.valghora.shrimpl.data.TemplateProvider
import com.valghora.shrimpl.data.WebAppStore
import com.valghora.shrimpl.icons.IconRenderer
import com.valghora.shrimpl.install.InstallResult
import com.valghora.shrimpl.install.Installer
import com.valghora.shrimpl.core.build.BuildCancelledException
import com.valghora.shrimpl.core.build.BuildException
import com.valghora.shrimpl.core.build.WebAppBuilder
import com.valghora.shrimpl.core.build.WebAppSpec
import com.valghora.shrimpl.core.icons.IconException
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.profile.resolvedIconForeground
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.WebAppRecord
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** What to build: settings, icon options, images that are new or changed (by file name), and the existing record. */
class BuildRequest(
    val profile: Profile,
    val icons: IconOptions,
    val newImages: Map<String, ByteArray>,
    val existing: WebAppRecord?,
)

sealed interface BuildOutcome {
    data class Installed(val record: WebAppRecord) : BuildOutcome

    data object NeedsUnknownSources : BuildOutcome

    data class Failed(val message: String, val installMethodUnavailable: Boolean = false) : BuildOutcome
}

/**
 * Makes and installs a web app (docs/SHRIMPL_SPEC.md R8, R6): renders the icon, builds and signs from
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
                val source = request.icons.icon.toSource { file -> request.newImages[file] ?: store.image(profile.appId, file) }
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
                    val record = request.existing?.installed(profile, request.icons, buildNumber, template.version, built.signerFingerprint, now)
                        ?: WebAppRecord.create(profile, request.icons, template.version, built.signerFingerprint, now)
                    store.save(record, request.newImages)
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
        } catch (e: com.valghora.shrimpl.core.build.SigningKeyException) {
            return BuildOutcome.Failed(e.message ?: "The signing key is unusable")
        } finally {
            output.delete()
        }
    }

    companion object {
        /** The letter used for themed and notification icons of image icons. */
        fun letterOf(name: String): Char = IconOptions.letterFor(name)
    }
}
