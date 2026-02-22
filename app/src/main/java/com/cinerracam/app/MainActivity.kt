package com.cinerracam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.cinerracam.app.ui.CinerraCamApp
import com.cinerracam.app.ui.RecorderViewModel

class MainActivity : ComponentActivity() {
    private val recorderViewModel: RecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CinerraCamApp(viewModel = recorderViewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        recorderViewModel.onAppForegroundChanged(isForeground = true)
    }

    override fun onStop() {
        recorderViewModel.onAppForegroundChanged(isForeground = false)
        super.onStop()
    }
}
