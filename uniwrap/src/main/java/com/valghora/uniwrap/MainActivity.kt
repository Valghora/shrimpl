package com.valghora.uniwrap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.valghora.uniwrap.ui.create.CreateScreen
import com.valghora.uniwrap.ui.home.HomeScreen
import com.valghora.uniwrap.ui.settings.SettingsScreen
import com.valghora.uniwrap.ui.theme.UniWrapTheme
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object CreateRoute

@Serializable
object SettingsRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle()
            UniWrapTheme(theme) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = HomeRoute) {
                    composable<HomeRoute> {
                        HomeScreen(onCreate = { nav.navigate(CreateRoute) }, onSettings = { nav.navigate(SettingsRoute) })
                    }
                    composable<CreateRoute> {
                        CreateScreen(
                            onClose = { nav.popBackStack() },
                            onBackUpKey = { nav.navigate(SettingsRoute) { popUpTo(HomeRoute) } },
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
