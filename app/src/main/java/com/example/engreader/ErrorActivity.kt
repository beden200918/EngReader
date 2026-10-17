package com.example.engreader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ErrorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "(没有错误内容)"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "启动崩溃记录"
            textSize = 22f
        }

        val body = TextView(this).apply {
            text = trace
            textSize = 12f
            setTextIsSelectable(true)
        }

        val scroll = ScrollView(this).apply { addView(body) }

        val copy = Button(this).apply {
            text = "复制错误信息"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
            }
        }

        val close = Button(this).apply {
            text = "关闭"
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(copy)
        root.addView(close)

        setContentView(root)
    }
}
