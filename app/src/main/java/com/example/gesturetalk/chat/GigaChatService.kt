package com.example.gesturetalk.chat

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class GigaChatService(private val apiKey: String) {

    companion object {
        private const val TAG = "GigaChatService"
        private const val AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
        private const val CHAT_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"
    }

    private var accessToken: String? = null

    init {
        // Настройка SSL для работы с самоподписанными сертификатами
        setupSSL()
    }

    private fun setupSSL() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up SSL", e)
        }
    }

    suspend fun sendMessage(message: String): String = withContext(Dispatchers.IO) {
        try {
            // Получаем токен доступа если его нет
            if (accessToken == null) {
                accessToken = getAccessToken()
            }

            // Отправляем запрос к GigaChat
            val response = sendChatRequest(message)
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            "Извините, произошла ошибка при обращении к GigaChat. Попробуйте позже."
        }
    }

    private fun getAccessToken(): String {
        val url = URL(AUTH_URL)
        val connection = url.openConnection() as HttpsURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("RqUID", UUID.randomUUID().toString())
            connection.setRequestProperty("Authorization", "Basic $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val postData = "scope=GIGACHAT_API_PERS"
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(postData)
            writer.flush()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(response)
                return jsonResponse.getString("access_token")
            } else {
                throw Exception("Failed to get access token: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sendChatRequest(message: String): String {
        val url = URL(CHAT_URL)
        val connection = url.openConnection() as HttpsURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val requestBody = JSONObject().apply {
                put("model", "GigaChat")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 512)
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.getJSONObject("message")
                    return messageObj.getString("content")
                }
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // Токен истек, получаем новый и повторяем запрос
                accessToken = getAccessToken()
                return sendChatRequest(message)
            }
            
            throw Exception("Failed to get response: $responseCode")
        } finally {
            connection.disconnect()
        }
    }
}
