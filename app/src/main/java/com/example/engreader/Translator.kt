package com.example.engreader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Translator {

    private var token: String? = null
    private var tokenExpireAt: Long = 0L

    suspend fun translate(ctx: Context, text: String): String = withContext(Dispatchers.IO) {
        val apiKey = Prefs.baiduApiKey(ctx)
        val secret = Prefs.baiduSecret(ctx)
        if (apiKey.isBlank() || secret.isBlank()) {
            return@withContext "尚未配置百度翻译的 API Key / Secret Key，请到设置页填写"
        }
        if (text.isBlank()) return@withContext ""
        if (text.length > 2000) return@withContext "文本过长（超过 2000 字符）"

        val (tk, tokenRaw) = getToken(apiKey, secret)
        if (tk.isNullOrBlank()) {
            return@withContext "获取 access_token 失败：$tokenRaw"
        }

        try {
            val url = URL("https://aip.baidubce.com/rpc/2.0/mt/texttrans/v1?access_token=$tk")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject().apply {
                put("from", "en")
                put("to", "zh")
                put("q", text)
            }.toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            val json = JSONObject(body)
            if (json.has("error_code")) {
                return@withContext "翻译失败（${json.optString("error_code")}）：${json.optString("error_msg")}"
            }
            val result = json.optJSONObject("result")
                ?: return@withContext "返回格式异常：$body"
            val arr = result.optJSONArray("trans_result")
                ?: return@withContext "返回格式异常：$body"
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                if (i > 0) sb.append("\n")
                sb.append(arr.getJSONObject(i).optString("dst"))
            }
            sb.toString()
        } catch (e: Exception) {
            "翻译出错：" + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun getToken(apiKey: String, secret: String): Pair<String?, String> {
        val now = System.currentTimeMillis()
        token?.let { if (now < tokenExpireAt && it.isNotBlank()) return Pair(it, "缓存") }

        return try {
            val url = URL(
                "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials" +
                    "&client_id=${URLEncoder.encode(apiKey, "UTF-8")}" +
                    "&client_secret=${URLEncoder.encode(secret, "UTF-8")}"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "POST"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val t = json.optString("access_token", "")
            val expires = json.optLong("expires_in", 0L)
            if (t.isBlank()) {
                Pair(null, body)
            } else {
                token = t
                tokenExpireAt = now + (expires - 300) * 1000
                Pair(t, body)
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: e.javaClass.simpleName)
        }
    }
}
