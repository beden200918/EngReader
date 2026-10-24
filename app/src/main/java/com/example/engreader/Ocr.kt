Num.2 type E与真嗣渚薰与汐名: 10-24 18:00:02
package com.example.engreader

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 拍照/相册图片 -> 纯文本。
 * 纯离线：ML Kit 的识别模型已打包进 APK，不联网、不上传照片。
 */
object Ocr {

    /** 识别一张图片。图片损坏等异常由调用方用 runCatching 捕获。 */
    suspend fun recognize(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val raw: String = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> cont.resume(result.text) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
            tidy(raw)
        } finally {
            recognizer.close()
        }
    }

    /**
     * 把识别出的原始文本整理成适合阅读的段落：
     * 1. 空行分段；
     * 2. 段内换行合并成空格（OCR 按行返回，不处理会变成一句一行）；
     * 3. 行尾连字符 + 下一个小写字母 => 去掉连字符直接拼（inter- + national）。
     */
    fun tidy(raw: String): String {
        val out = StringBuilder()
        val blocks = raw.split(Regex("\\n[ \\t]*\\n"))
        for (block in blocks) {
            val lines = block.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            val sb = StringBuilder()
            for ((i, line) in lines.withIndex()) {
                if (i == 0) {
                    sb.append(line)
                    continue
                }
                val prev = sb.toString()
                if (prev.endsWith("-") && line.first().isLowerCase()) {
                    sb.setLength(prev.length - 1)
                    sb.append(line)
                } else if (prev.endsWith("-")) {
                    sb.append(line)
                } else {
                    sb.append(' ').append(line)
                }
            }

            var para = sb.toString()
            para = para.replace(Regex("[\\t ]+"), " ")
            para = para.replace(Regex(" +([,.;:!?])"), "\$1")
            para = para.trim()
            if (para.isEmpty()) continue
            if (out.isNotEmpty()) out.append("\n\n")
            out.append(para)
        }
        return out.toString().trim()
    }
}


Num.2 type E与真嗣渚薰与汐名: 10-24 18:03:33
Ocr.kt
