package com.example.suretouchapp.ui.components

import androidx.compose.runtime.*
import com.example.suretouchapp.data.repository.ClassSchedulePolicy
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Re-evaluates visible join actions at the window boundary and after resume. */
@Composable
fun rememberClassSessionTime(): LocalDateTime {
    var now by remember { mutableStateOf(ClassSchedulePolicy.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ClassSchedulePolicy.now()
            delay(1_000)
        }
    }
    return now
}
