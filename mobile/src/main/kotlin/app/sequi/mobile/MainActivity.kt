package app.sequi.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lib = (application as SequiPhoneApp).library
        setContent {
            SequiTheme {
                Surface { Root(lib) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WatchLink.refresh(this)
        app.sequi.data.SequiSync.pull()
    }
}

@Composable
private fun Root(lib: app.sequi.data.Library) {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) } // null = list, "new" or an id
    var editingAlarm by remember { mutableStateOf<String?>(null) }

    val a = editingAlarm
    if (a != null) {
        BackHandler { editingAlarm = null }
        AlarmEditorScreen(lib, a, onClose = { editingAlarm = null })
        return
    }
    val e = editing
    if (e != null) {
        BackHandler { editing = null }
        EditorScreen(lib, e, onClose = { editing = null })
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.List, null) }, label = { Text("Timers") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.Notifications, null) }, label = { Text("Alarms") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text("Stats") })
            }
        },
    ) { pad ->
        if (tab == 0) TimersScreen(lib, onOpen = { editing = it }, modifier = Modifier.padding(pad))
        else if (tab == 1) AlarmsScreen(lib, onOpen = { editingAlarm = it }, modifier = Modifier.padding(pad))
        else StatsScreen(lib, modifier = Modifier.padding(pad))
    }
}
