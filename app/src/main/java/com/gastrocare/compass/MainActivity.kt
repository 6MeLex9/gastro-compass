package com.gastrocare.compass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gastrocare.compass.data.AppRepository
import com.gastrocare.compass.ui.AppRoot

/**
 * Единственная activity: всё остальное — экраны Compose.
 * Хранилище создаётся один раз и передаётся в дерево композиции.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = AppRepository(applicationContext)
        setContent {
            AppRoot(repository)
        }
    }
}
