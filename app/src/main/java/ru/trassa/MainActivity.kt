package ru.trassa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ru.trassa.data.TrassaRepository
import ru.trassa.theme.TrassaColors
import ru.trassa.ui.TrassaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тёмная тема как в макете: светлые иконки статус-бара на фоне #101214
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val repository = TrassaRepository(applicationContext)
        setContent {
            Box(Modifier.fillMaxSize().background(TrassaColors.Background)) {
                TrassaApp(repository)
            }
        }
    }
}
