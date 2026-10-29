package com.valghora.shrimple.webapp

import com.valghora.shrimple.core.DesktopSessionController
import com.valghora.shrimple.core.UaMode
import com.valghora.shrimple.core.UaStore
import com.valghora.shrimple.core.UrlClass
import com.valghora.shrimple.core.ZoomStore
import com.valghora.shrimple.core.profile.FieldError
import com.valghora.shrimple.core.profile.ProfileCodec
import com.valghora.shrimple.core.profile.ProfileValidator
import com.valghora.shrimple.core.profile.sessionConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks the profile this build ships in assets/shrimple/profile.json, which WebApp reads at launch. */
class ProfileAssetTest {
    private val file = File(requireNotNull(System.getProperty("profileJson")) { "profileJson not set by Gradle" })
    private val profile = ProfileCodec.decode(file.readText())

    @Test
    fun profileIsValid() {
        assertEquals(emptyList<FieldError>(), ProfileValidator.validate(profile))
    }

    @Test
    fun startUrlLoadsInsideTheApp() {
        val controller = DesktopSessionController(
            zoomStore = object : ZoomStore {
                override fun load(): Int? = null
                override fun save(value: Int) = Unit
            },
            uaStore = object : UaStore {
                override var userChoice: UaMode? = null
                override var learnedDesktop: Boolean = false
            },
            config = profile.sessionConfig(),
        )
        assertEquals(UrlClass.INTERNAL, controller.classify(profile.startUrl))
    }
}
