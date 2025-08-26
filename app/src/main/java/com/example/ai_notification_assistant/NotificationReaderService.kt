package com.example.ai_notification_assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object NotificationData {
    private val _latestNotification = MutableStateFlow<NotificationItem?>(null)
    val latestNotification = _latestNotification.asStateFlow()

    fun updateNotification(item: NotificationItem) {
        _latestNotification.value = item
    }
}

data class NotificationItem(val title: String, val text: String, val suggestion: String)

class NotificationReaderService : NotificationListenerService() {

    val apiKey = BuildConfig.OPENROUTER_API_KEY
    val openRouterApi = OpenRouterService.create(apiKey)
    val repo = OpenRouterRepository(openRouterApi)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title", "")
        val textCharSeq = extras.getCharSequence("android.text")
        val text = textCharSeq?.toString() ?: ""

        if (text.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val suggestion = generateSuggestion(text)
                val item = NotificationItem(title, text, suggestion)
                NotificationData.updateNotification(item)

                Log.d("NotificationAI", "Notification: $title | $text | Suggestion: $suggestion")
            }
        }
    }

    private suspend fun generateSuggestion(text: String): String {
        return try {
            repo.suggestReply(text)
        } catch (e: Exception) {
            e.printStackTrace()
            "Error generating suggestion"
        }
    }
}
