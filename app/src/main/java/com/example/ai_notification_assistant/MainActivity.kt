package com.example.ai_notification_assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

var viewTreeOwner: Any? = null

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewTreeOwner = this.savedStateRegistry
        requestOverlayPermission()
        requestNotificationPermission()

        setContent {
            FancyHomeScreen()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please allow overlay permission", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestNotificationPermission() {

        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )

        if (enabledListeners == null || !enabledListeners.contains(packageName)) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Please enable Notification Access for this app",
                Toast.LENGTH_LONG
            )
                .show()
        }
    }
}

fun hasNotificationPermission(context: android.content.Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return enabledListeners != null && enabledListeners.contains(context.packageName)
}


@Composable
fun FancyHomeScreen() {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F4F8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // App Title
            Text(
                text = "AI Notification Assistant",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1F3B)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle / tagline
            Text(
                text = "Get smart replies for your notifications instantly!",
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Feature Tags / Dummy Features
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeatureTag("Smart Replies")
                FeatureTag("Copy Suggestions")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureTag("Notification Reader")
                FeatureTag("Interactive")
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Start Assistant Button
            Button(
                onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        if (hasNotificationPermission(context)){
                            startFloatingService(context)
                        } else {
                            Toast.makeText(
                                context,
                                "Please grant notification access",
                                Toast.LENGTH_LONG
                            ).show()
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        }

                    } else {
                        Toast.makeText(
                            context,
                            "Please grant overlay permission",
                            Toast.LENGTH_LONG
                        ).show()
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$context.packageName")
                        )
                        context.startActivity(intent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Start Assistant",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Some Dummy Cards for Features
            FeatureCard("AI Suggestion", "Read notification content and suggest replies.")
            FeatureCard("Floating Overlay", "Shows suggestions without opening the app.")
            FeatureCard("Copy & Use", "Copy suggestions and use them anywhere easily.")
        }
    }
}

fun startFloatingService(context: android.content.Context) {
    val intent = Intent(context, FloatingService::class.java)
    ContextCompat.startForegroundService(context, intent)
    Toast.makeText(context, "Floating Service Started", Toast.LENGTH_SHORT)
        .show()
}

@Composable
fun FeatureTag(tagName: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFEEF2FF), shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = tagName,
            color = Color(0xFF4F46E5),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
fun FeatureCard(title: String, description: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Color.White)
                .padding(16.dp)
                .fillMaxWidth()

        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1F3B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}
