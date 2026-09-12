package me.bmax.apatch.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.rememberNavHostEngine
import me.bmax.apatch.root.RootCapabilityRepository
import me.bmax.apatch.root.RootCheckPhase
import me.bmax.apatch.root.isUsable
import me.bmax.apatch.ui.home.HomeWallpaperViewModel
import me.bmax.apatch.ui.home.LocalHomeWallpaperViewModel
import me.bmax.apatch.ui.shell.AsterAppShell
import me.bmax.apatch.ui.shell.AsterNavigationCapabilities
import me.bmax.apatch.ui.shell.AsterNavigationTransitions
import me.bmax.apatch.ui.shell.PrimaryDestination
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import top.yukonga.miuix.kmp.basic.SnackbarHostState

class MainActivity : AppCompatActivity() {

    private var isLoading = true

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen().setKeepOnScreenCondition { isLoading }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            APatchTheme {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                // Activity scoped so the shell can paint the home scene backdrop from the same
                // wallpaper state the home screen edits.
                val homeWallpaperViewModel: HomeWallpaperViewModel = viewModel()
                val primaryRoutes = remember {
                    PrimaryDestination.entries.map { it.direction.route }.toSet()
                }
                val rootCapability by RootCapabilityRepository.snapshot.collectAsStateWithLifecycle()
                val capabilities = AsterNavigationCapabilities(
                    kernelPatchReady = rootCapability.kernelPatch.isUsable(),
                    androidPatchReady = rootCapability.androidPatch.isUsable(),
                )

                val defaultTransitions = remember(primaryRoutes) {
                    AsterNavigationTransitions(primaryRoutes)
                }

                LaunchedEffect(Unit) {
                    SuperUserViewModel().ensureAppListLoaded()
                }

                CompositionLocalProvider(
                    LocalHomeWallpaperViewModel provides homeWallpaperViewModel,
                ) {
                    AsterAppShell(
                        navController = navController,
                        snackbarHostState = snackBarHostState,
                        capabilities = capabilities,
                    ) { navHostModifier ->
                        DestinationsNavHost(
                            modifier = navHostModifier,
                            navGraph = NavGraphs.root,
                            navController = navController,
                            engine = rememberNavHostEngine(
                                navHostContentAlignment = Alignment.TopCenter
                            ),
                            defaultTransitions = defaultTransitions,
                        )
                    }
                }
            }
        }

        SingletonImageLoader.setSafe(
            SingletonImageLoader.Factory { context ->
                ImageLoader.Builder(context)
                    .components {
                        add(AppIconKeyer())
                        add(AppIconFetcher.Factory(context))
                    }
                    .build()
            }
        )

        isLoading = false
    }
}
