package com.example.ai_notification_assistant

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

// --- Request / Response Models ---
data class ORMessage(
    val role: String,
    val content: String
)

data class ORRequest(
    val model: String, // e.g. "openai/gpt-3.5-turbo" or "anthropic/claude-3-opus"
    val messages: List<ORMessage>,
    val max_tokens: Int = 60
)

data class ORChoice(val message: ORMessage)
data class ORResponse(val choices: List<ORChoice>)

// --- Retrofit API ---
interface OpenRouterApi {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun getChatCompletion(@Body request: ORRequest): ORResponse
}

// --- Retrofit Builder ---
object OpenRouterService {
    private const val BASE_URL = "https://openrouter.ai/api/v1/"

    fun create(apiKey: String): OpenRouterApi {
        val logging = HttpLoggingInterceptor().apply {
            setLevel(HttpLoggingInterceptor.Level.BODY)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "your-app-url-or-package-name") // OpenRouter requires this
                    .addHeader("X-Title", "AI Notification Assistant") // optional: app name
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(OpenRouterApi::class.java)
    }
}
