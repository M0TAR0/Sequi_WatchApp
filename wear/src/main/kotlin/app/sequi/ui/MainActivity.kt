package app.sequi.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.sequi.alarm.AlarmScheduler
import app.sequi.data.Repo
import app.sequi.timer.HrPermission
import app.sequi.timer.TimerBus
import app.sequi.timer.TimerService

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startRequest.value = intent.getStringExtra(EXTRA_START)
    }

    companion object {
        /** Set by the Tile: start this timer as soon as the app is up. */
        const val EXTRA_START = "start_id"
        val startRequest = mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startRequest.value = intent?.getStringExtra(EXTRA_START)
        runCatching { AlarmScheduler.reschedule(this) }
        setContent {
            SequiTheme {
                AppScaffold { SequiNav() }
            }
        }
    }
}

@Composable
fun SequiNav() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val nav = rememberSwipeDismissableNavController()

    // Permissions: notifications (ongoing activity + alarms) and heart rate (when a timer starts).
    var pendingStart by remember { mutableStateOf<String?>(null) }
    fun launchTimer(id: String) {
        TimerBus.clear()
        TimerService.start(ctx, id)
        nav.navigate("run")
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pendingStart?.let { launchTimer(it) }
        pendingStart = null
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (TimerBus.state.value.active) nav.navigate("run")
    }
    val startTimer: (String) -> Unit = { id ->
        val wantHr = Repo.settings.value.heartRate && !HrPermission.granted(ctx)
        if (wantHr) { pendingStart = id; perms.launch(arrayOf(HrPermission.name)) } else launchTimer(id)
    }

    val request by MainActivity.startRequest
    LaunchedEffect(request) {
        request?.let { id ->
            MainActivity.startRequest.value = null
            if (Repo.sequence(id) != null && !TimerBus.state.value.active) startTimer(id)
        }
    }

    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onStart = startTimer,
                onEdit = { nav.navigate("edit/$it") },
                onNew = { nav.navigate("edit/new") },
                onRun = { nav.navigate("run") },
                onAlarms = { nav.navigate("alarms") },
                onStats = { nav.navigate("stats") },
                onTimes = { nav.navigate("times") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("run") { RunScreen(onExit = { nav.popBackStack("home", false) }) }
        composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            EditorScreen(it.arguments?.getString("id") ?: "new", onDone = { nav.popBackStack() })
        }
        composable("alarms") { AlarmsScreen(onEdit = { nav.navigate("alarm/$it") }) }
        composable("alarm/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            AlarmEditScreen(it.arguments?.getString("id") ?: "new", onDone = { nav.popBackStack() })
        }
        composable("stats") { StatsScreen() }
        composable("times") { TimesScreen() }
        composable("settings") { SettingsScreen() }
    }
}
