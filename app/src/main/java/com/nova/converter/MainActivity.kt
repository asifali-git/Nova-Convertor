package com.nova.converter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nova.converter.core.Notifications
import com.nova.converter.core.Outputs
import com.nova.converter.core.Queue
import com.nova.converter.features.history.HistoryScreen
import com.nova.converter.features.home.HomeScreen
import com.nova.converter.features.queue.MiniJobsBar
import com.nova.converter.features.queue.QueueScreen
import com.nova.converter.features.tool.ToolDetailScreen
import com.nova.converter.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {

    private var shareUri by androidx.compose.runtime.mutableStateOf<Uri?>(null)
    private var openQueueRequest by androidx.compose.runtime.mutableStateOf(false)
    private var startTabRequest by androidx.compose.runtime.mutableStateOf<String?>(null)

    private val notifPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    /**
     * Language override — Settings.init() yahan tak aata nahi hai, isliye pref
     * seedha padha jaata hai. "system" = phone ki language follow hoti hai.
     * App language Settings se badalne par Activity recreate hoti hai.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLocale(newBase))
    }

    private fun applyLocale(base: Context): Context {
        val code = base.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
            .getString("language", "system") ?: "system"
        if (code.isBlank() || code == "system") return base
        val loc = java.util.Locale.forLanguageTag(code)
        if (loc.language.isBlank()) return base
        java.util.Locale.setDefault(loc)
        val cfg = android.content.res.Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLayoutDirection(loc)
        return base.createConfigurationContext(cfg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Outputs.init(applicationContext)
        com.nova.converter.core.Settings.init(applicationContext)
        com.nova.converter.core.Notifications.ensure(applicationContext)
        requestNotificationPermission()
        shareUri = handleShare(intent)
        openQueueRequest = intent?.getBooleanExtra(EXTRA_OPEN_QUEUE, false) == true
        startTabRequest = handleShortcut(intent)
        handleOpenMedia(intent)
        setContent {
            NovaTheme {
                NovaApp(
                    initial = shareUri,
                    openQueue = openQueueRequest,
                    onQueueOpened = { openQueueRequest = false },
                    startTab = startTabRequest,
                    onTabConsumed = {
                        startTabRequest = null
                    },
                    onShareConsumed = { shareUri = null }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shareUri = handleShare(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_QUEUE, false)) openQueueRequest = true
        handleShortcut(intent)?.let { startTabRequest = it }
        handleOpenMedia(intent)
    }

    /**
     * App screen par hai ya nahi — `Notifications` isse "File completed"
     * notification ko foreground me **kaagaz pe hi nahi** bana deta hai.
     * User ko wahi result already screen par dikh raha hota hai, aur har
     * conversion pe notification aane se Android daily limit maar deta tha
     * ("5 notifications per day", "3 notifications per day").
     */
    override fun onResume() {
        super.onResume()
        Notifications.appOnScreen = true
    }

    override fun onPause() {
        super.onPause()
        Notifications.appOnScreen = false
    }

    private fun handleShare(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            return intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            return list?.firstOrNull()
        }
        return null
    }

    /** App icon long-press shortcuts → nova://home | nova://history | nova://queue */
    private fun handleShortcut(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "nova") return null
        return when (data.host) {
            "home", "convert" -> "home"
            "history" -> "history"
            "queue" -> "queue"
            "settings" -> "settings"
            else -> null
        }
    }

    companion object {
        const val EXTRA_OPEN_QUEUE = "com.nova.converter.OPEN_QUEUE"

        /** Notification tap / share se file built-in player me kholne ke liye */
        const val EXTRA_OPEN_MEDIA = "com.nova.converter.OPEN_MEDIA"
        const val EXTRA_OPEN_MEDIA_MIME = "com.nova.converter.OPEN_MEDIA_MIME"
    }

    /**
     * Intent se media path uthakar app ka built-in player khol do. File abhi
     * exist nahi karti (cache clear ho gaya) to kuch nahi hota — crash nahi.
     */
    private fun handleOpenMedia(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_OPEN_MEDIA) ?: return
        if (path.isBlank()) return
        intent.removeExtra(EXTRA_OPEN_MEDIA)
        if (!java.io.File(path).exists()) return
        val mime = intent.getStringExtra(EXTRA_OPEN_MEDIA_MIME).orEmpty()
            .ifBlank { com.nova.converter.core.FastCompressor.guessMime(path.substringAfterLast('.', "")) }
        com.nova.converter.features.player.PlayerController.open(path, mime)
    }
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", R.string.nav_home, Icons.Filled.Home),
    Tab("history", R.string.nav_history, Icons.Filled.History),
    Tab("queue", R.string.nav_queue, Icons.Filled.List),
    Tab("settings", R.string.nav_settings, Icons.Filled.Settings)
)

@Composable
fun NovaApp(
    initial: Uri?,
    openQueue: Boolean = false,
    onQueueOpened: () -> Unit = {},
    startTab: String? = null,
    onTabConsumed: () -> Unit = {},
    onShareConsumed: () -> Unit = {}
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val onToolRoute = current?.startsWith("tool/") == true
    val appContext = androidx.compose.ui.platform.LocalContext.current

    fun goToQueue() {
        nav.navigate("queue") {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goToTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // notification tap → seedha live queue
    LaunchedEffect(openQueue) {
        if (openQueue) {
            goToQueue()
            onQueueOpened()
        }
    }

    // icon long-press shortcut (home / history / queue / settings)
    LaunchedEffect(startTab) {
        val tab = startTab ?: return@LaunchedEffect
        goToTab(tab)
        onTabConsumed()
    }

    // Share-to-Nova: shared uri → cache me copy → mime ke hisaab se tool kholo
    LaunchedEffect(initial) {
        val uri = initial ?: return@LaunchedEffect
        val mime = try {
            appContext.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        val toolId = when {
            mime?.startsWith("audio") == true -> "audio_converter"
            mime?.startsWith("image") == true -> "image_converter"
            else -> "video_converter"
        }
        val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (uri.scheme == "file") uri.path
            else com.nova.converter.core.Files.copyToCache(appContext, uri)?.absolutePath
        }
        if (path != null) {
            nav.navigate("tool/$toolId?path=${Uri.encode(path)}")
            onShareConsumed()
        } else {
            snackbar.showSnackbar(appContext.getString(R.string.err_open_shared))
            onShareConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
                // minimize ki hui jobs ki global bar (job chalti rahegi, bar dikhati rahegi)
                if (current != "queue") {
                    MiniJobsBar(onClick = { goToQueue() })
                }
                if (!onToolRoute) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    onTool = { id -> nav.navigate("tool/$id") }
                )
            }
            composable(
                route = "tool/{toolId}?path={path}",
                arguments = listOf(
                    navArgument("toolId") { type = NavType.StringType },
                    navArgument("path") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                ToolDetailScreen(
                    toolId = entry.arguments?.getString("toolId") ?: "",
                    snackbar = snackbar,
                    onBack = { nav.popBackStack() },
                    initialPath = entry.arguments?.getString("path")
                )
            }
            composable("history") { HistoryScreen(snackbar = snackbar) }
            composable("settings") {
                com.nova.converter.features.settings.SettingsScreen(
                    snackbar = snackbar,
                    onOpenLegal = { kind -> nav.navigate("legal/$kind") }
                )
            }
            composable(
                route = "legal/{kind}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType })
            ) { entry ->
                com.nova.converter.features.settings.LegalScreen(
                    kind = entry.arguments?.getString("kind") ?: "licenses",
                    onBack = { nav.popBackStack() }
                )
            }
            composable("queue") {
                QueueScreen(
                    snackbar = snackbar,
                    onBrowse = {
                        nav.navigate("home") {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }

        // full-screen in-app player overlay (result card / history "Play" se)
        val playerMedia = com.nova.converter.features.player.PlayerController.current
        if (playerMedia.value != null) {
            val media = playerMedia.value!!
            androidx.activity.compose.BackHandler {
                com.nova.converter.features.player.PlayerController.close()
            }
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                com.nova.converter.features.player.PlayerScreen(
                    snackbar = snackbar,
                    initialPath = media.first,
                    onClose = { com.nova.converter.features.player.PlayerController.close() }
                )
            }
        }
        } // Box (overlay host)
    }
}
