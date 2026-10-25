package com.valghora.wrapper.core.build

import com.valghora.wrapper.core.profile.Profile
import com.valghora.wrapper.core.profile.ProfileCodec
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Builds sample web apps for trying on a device, only when UNIWRAP_SAMPLES_OUT names a directory:
 *
 *     UNIWRAP_SAMPLES_OUT=/tmp/samples ./gradlew :core:test --tests '*SampleWebApps*'
 *
 * Both are signed with one key, made for the run, as UniWrap will do.
 */
class SampleWebApps {
    @Test
    fun buildSamples() {
        val out = System.getenv("UNIWRAP_SAMPLES_OUT")?.let(::File)
        assumeTrue("set UNIWRAP_SAMPLES_OUT to build sample web apps", out != null)
        out!!.mkdirs()
        val discord = ProfileCodec.decode(javaClass.getResource("/profiles/discord.json")!!.readText())
        val github = Profile(
            name = "GitHub",
            appId = "github",
            startUrl = "https://github.com/",
            internalDomains = listOf("github.com", "githubusercontent.com", "githubassets.com"),
            iconBackground = "#181717",
            headerColor = "#0D1117",
        )
        for (profile in listOf(discord, github)) {
            val built = WebAppBuilder.build(TemplateFixture.apk, File(out, "${profile.appId}.apk"), WebAppSpec(profile, 1), TemplateFixture.key)
            println("${built.apk}: ${built.packageName} ${built.versionName} signer ${built.signerFingerprint}")
        }
    }
}
