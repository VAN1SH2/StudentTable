package ru.sfu.student

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import ru.sfu.student.ui.StudentShell
import ru.sfu.student.ui.theme.StudentTheme

class MainActivity : ComponentActivity() {
    private var requestedScreen by mutableIntStateOf(0)
    private var navigationRequest by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedScreen = intent.getIntExtra("screen", 0)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.WHITE))
        setContent { StudentTheme { StudentShell(requestedScreen, navigationRequest) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        requestedScreen = intent.getIntExtra("screen", 0); navigationRequest++
    }
    override fun onResume() {
        super.onResume()
        val app = application as StudentApp
        app.scope.launch { runCatching { app.reminders.rebuild() } }
    }
}
