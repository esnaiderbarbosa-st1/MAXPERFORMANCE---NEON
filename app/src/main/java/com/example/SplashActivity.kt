package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.screens.AnimatedSplashContent
import com.example.ui.theme.MyApplicationTheme

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AnimatedSplashContent(
                    onAnimationComplete = {
                        val mainIntent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                            if (intent != null && intent.extras != null) {
                                putExtras(intent.extras!!)
                            }
                        }
                        startActivity(mainIntent)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                        } else {
                            @Suppress("DEPRECATION")
                            overridePendingTransition(0, 0)
                        }
                        finish()
                    }
                )
            }
        }
    }
}
