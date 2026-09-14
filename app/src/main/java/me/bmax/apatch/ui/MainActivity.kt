package me.bmax.apatch.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.ramcosta.composedestinations.generated.destinations.ExecuteAPMActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.MainScreenDestination
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.rememberNavHostEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.bmax.apatch.root.RootCapabilityRepository
import me.bmax.apatch.root.RootCheckPhase
import me.bmax.apatch.root.isUsable
import me.bmax.apatch.ui.home.HomeWallpaperViewModel
import me.bmax.apatch.ui.home.LocalHomeWallpaperViewModel
import me.bmax.apatch.ui.intake.ExternalFileReader
import me.bmax.apatch.ui.intake.ExternalFileRequests
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_ID_PARAM
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_SCHEME
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_TOKEN_PARAM
import me.bmax.apatch.ui.module.ModuleShortcutRequest
import me.bmax.apatch.ui.module.ModuleShortcutRequests
import me.bmax.apatch.ui.module.resolveModuleShortcutRequest
import me.bmax.apatch.ui.shell.AppDensity
import me.bmax.apatch.ui.shell.AsterAppShell
import me.bmax.apatch.ui.shell.AsterNavigationCapabilities
import me.bmax.apatch.ui.shell.AsterNavigationTransitions
import me.bmax.apatch.ui.shell.PrimaryDestination
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.theme.LocalThemeModeState
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.ModuleShortcut
import top.yukonga.miuix.kmp.basic.SnackbarHostState

class MainActivity : AppCompatActivity() {

    private companion object {
        const val ContentScheme = "content"
        const val FileScheme = "file"
        const val TAG = "MainActivity"

        /**
         * How long a shortcut request waits for the navigation host to have a graph. The host sets
         * it during its own composition, so in practice this is a formality.
         */
        const val NAVIGATION_READY_TIMEOUT_MS = 5_000L
    }

    private var isLoading = true

    /**
     * The size the app draws itself in is carried on the Context, so it has to be in place before
     * the window is: whatever the appearance page chose is applied here, and leaving it alone
     * leaves the device's own density alone with it.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppDensity.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen().setKeepOnScreenCondition { isLoading }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        handleModuleShortcut(intent)
        handleExternalFile(intent)

        setContent {
            APatchTheme {
                val isDark = LocalThemeModeState.current.isDark
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                // Activity scoped so the shell can paint the home scene backdrop from the same
                // wallpaper state the home screen edits.
                val homeWallpaperViewModel: HomeWallpaperViewModel = viewModel()
                // The wallpaper follows the theme the app actually resolved, not the raw system
                // setting, so the manual dark mode switch moves the picture too.
                LaunchedEffect(isDark) {
                    homeWallpaperViewModel.setDarkTheme(isDark)
                }
                val primaryRoutes = remember {
                    PrimaryDestination.entries.map { it.direction.route }.toSet() + MainScreenDestination.route
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

                    // A launcher shortcut arrives as an intent, but where it lands is a
                    // destination, so the two are kept apart: the intent publishes a request and
                    // this collects it. Subscribing instead of reading the intent once is what
                    // makes a second tap, on an already running manager, work.
                    LaunchedEffect(navController) {
                        ModuleShortcutRequests.pending.collect { request ->
                            if (request !is ModuleShortcutRequest.ExecuteAction) return@collect
                            // Waiting on the host's own back stack, not on currentDestination: that
                            // is a plain getter, so a snapshotFlow over it would read null once and
                            // never be woken again. This flow emits as soon as the host has a graph
                            // and an entry to show for it.
                            val ready = withTimeoutOrNull(NAVIGATION_READY_TIMEOUT_MS) {
                                navController.currentBackStackEntryFlow.first()
                            } != null
                            if (!ready) {
                                Log.w(TAG, "no navigation graph for ${request.moduleId}; dropping the request")
                                ModuleShortcutRequests.consume()
                                return@collect
                            }
                            navController.navigate(ExecuteAPMActionScreenDestination(request.moduleId).route)
                            ModuleShortcutRequests.consume()
                        }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The shortcut is allowed to bring this activity to the front instead of starting another
        // copy, so the second and later taps arrive here.
        setIntent(intent)
        handleModuleShortcut(intent)
        handleExternalFile(intent)
    }

    /**
     * Takes the file another app opened or shared with the manager.
     *
     * What the file is takes reading it, which is not something to do on the main thread, and acting
     * on it needs dialogs and a snackbar, which live inside the shell. So the reading happens here
     * and the decision is handed on.
     */
    private fun handleExternalFile(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

            else -> null
        } ?: return
        // Our own links are handled above; this is only for files another app handed over.
        if (uri.scheme != ContentScheme && uri.scheme != FileScheme) return
        lifecycleScope.launch {
            ExternalFileReader.read(this@MainActivity, uri)?.let(ExternalFileRequests::publish)
        }
    }

    /**
     * Accepts the deep link a module shortcut carries.
     *
     * This activity is the launcher activity and therefore exported, so the link is only believed
     * when it carries the token this install generated: the launcher is not the only thing on the
     * device that can send it an explicit intent, and what the action link ends up running is a
     * module's script as root.
     */
    private fun handleModuleShortcut(intent: Intent?) {
        val data = intent?.data ?: return
        // Checked before the token so an ordinary launch does not mint one.
        if (data.scheme != MODULE_SHORTCUT_SCHEME) return
        val request = resolveModuleShortcutRequest(
            scheme = data.scheme,
            host = data.host,
            moduleId = data.getQueryParameter(MODULE_SHORTCUT_ID_PARAM),
            token = data.getQueryParameter(MODULE_SHORTCUT_TOKEN_PARAM),
            expectedToken = ModuleShortcut.token(this),
        ) ?: return

        when (request) {
            is ModuleShortcutRequest.ExecuteAction -> ModuleShortcutRequests.publish(request)
            is ModuleShortcutRequest.OpenWebUi -> startActivity(
                Intent(this, WebUIActivity::class.java)
                    .setData("apatch://webui/${request.moduleId}".toUri())
                    .putExtra("id", request.moduleId)
                    .putExtra("name", ModuleShortcut.moduleNameOf(intent).ifEmpty { request.moduleId }),
            )
        }
    }
}
