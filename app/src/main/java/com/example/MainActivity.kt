package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.SanaApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SanaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SanaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SanaApp(viewModel = viewModel)
            }
        }
    }
}

