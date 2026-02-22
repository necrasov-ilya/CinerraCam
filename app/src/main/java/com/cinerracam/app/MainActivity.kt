package com.cinerracam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cinerracam.app.ui.CinerraCamApp
import com.cinerracam.app.ui.RecorderViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: RecorderViewModel = viewModel()
            CinerraCamApp(viewModel = viewModel)
        }
    }
}
