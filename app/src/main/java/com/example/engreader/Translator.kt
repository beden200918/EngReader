package com.example.engreader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

object Translator {

    suspend fun translate(ctx: Context, text: String): String = withContext(Dispatchers.IO) {
        val appId = Prefs.baiduAppId(ctx)
        val apiKey = Prefs.baiduApiKey(ctx)
        val secret = Prefs.baiduSecret(ctx)
        if (appId.isBlank() || apiKey.isBlank() || secret.isBlank()) {
            return@withContext "尚未配置百度翻译密钥，请到设置页填写"
        }
        if (text.isBlank()) return@withContext ""

        try {
            val salt = System.currentTimeMillis().toString()
            val sign = md5(appId + text + salt + secret)
            val q = URLEncoder.encode(text, "UTF-8")
            val url = URL(
                "https://fanyi-api.baidu.com/api/trans/vip/translate" +
                    "?q=$q&from=en&to=zh&appid=$appId&salt=$salt&sign=$sign"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            if (json.has("error_code")) {
                val code = json.optString("error_code", "?")
                val msg = json.optString("error_msg", "")
                return@withContext "翻译失败（$code）：$msg"
            }
            val arr = json.getJSONArray("trans_result")
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

    private fun md5(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
