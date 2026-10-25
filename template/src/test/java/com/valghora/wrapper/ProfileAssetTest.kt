package com.valghora.wrapper

import com.valghora.wrapper.core.DesktopSessionController
import com.valghora.wrapper.core.UaMode
import com.valghora.wrapper.core.UaStore
import com.valghora.wrapper.core.UrlClass
import com.valghora.wrapper.core.ZoomStore
import com.valghora.wrapper.core.profile.FieldError
import com.valghora.wrapper.core.profile.ProfileCodec
import com.valghora.wrapper.core.profile.ProfileValidator
import com.valghora.wrapper.core.profile.sessionConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks the profile this build ships in assets/uniwrap/profile.json, which WebApp reads at launch. */
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
