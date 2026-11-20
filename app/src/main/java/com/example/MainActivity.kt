package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.navigation.PocketCodeNavGraph
import com.example.ui.theme.PocketCodeTheme
import com.example.ui.theme.resolveIsDarkTheme
import com.example.viewmodel.PocketCodeViewModel

class MainActivity : ComponentActivity() {

    private val pocketViewModel: PocketCodeViewModel by lazy {
        ViewModelProvider(
            this,
            PocketCodeViewModel.provideFactory(applicationContext)
        )[PocketCodeViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val uiState by pocketViewModel.uiState.collectAsStateWithLifecycle()
            val isDark = resolveIsDarkTheme(uiState.themeMode)

            LaunchedEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            PocketCodeTheme(
                themeMode = uiState.themeMode,
                dynamicColorEnabled = uiState.dynamicColorEnabled
            ) {
                PocketCodeNavGraph(viewModel = pocketViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        pocketViewModel.onAppForegrounded()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pocketViewModel.onAppBackgrounded()
    }

    override fun onPause() {
        super.onPause()
        pocketViewModel.onActivityPaused()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            pocketViewModel.onAppBackgrounded()
        }
    }
}
