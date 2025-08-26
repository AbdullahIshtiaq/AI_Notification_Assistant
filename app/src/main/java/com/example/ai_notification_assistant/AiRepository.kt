package com.example.ai_notification_assistant


class OpenRouterRepository(private val api: OpenRouterApi) {
    suspend fun suggestReply(message: String): String {
        return try {
            val response = api.getChatCompletion(
                ORRequest(
                    model = "openai/gpt-3.5-turbo", // or "mistralai/mixtral-8x7b"
                    messages = listOf(
                        ORMessage("system", "You are an assistant that suggests short, casual replies to notifications."),
                        ORMessage("user", "Message: $message")
                    ),
                    max_tokens = 50
                )
            )
            response.choices.firstOrNull()?.message?.content ?: "No suggestion"
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }
}

