package com.example.lens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.lens.data.ConfigStore
import com.example.lens.ui.LensApp
import com.example.lens.ui.LensViewModel
import com.example.lens.ui.theme.LensTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val configStore = ConfigStore(this)
        
        // Comprehensive initial permission check
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }

        setContent {
            LensTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: LensViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return LensViewModel(configStore) as T
                            }
                        }
                    )
                    LensApp(viewModel)
                }
            }
        }
    }
}
