package com.example.vkp

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    companion object {
        val historyList = mutableListOf<RecognitionResult>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val container = findViewById<LinearLayout>(R.id.historyContainer)

        // Отображаем историю в обратном порядке (последние сверху)
        for (item in historyList.reversed()) {
            val row = layoutInflater.inflate(R.layout.item_history, container, false)

            val icon = row.findViewById<ImageView>(R.id.historyIcon)
            val text = row.findViewById<TextView>(R.id.historyText)
            val image = row.findViewById<ImageView>(R.id.historyImage)

            icon.setImageResource(item.iconRes)
            text.text = item.label
            image.setImageBitmap(item.bitmap)

            container.addView(row)
        }
    }
}

data class RecognitionResult(
    val label: String,
    val iconRes: Int,
    val bitmap: android.graphics.Bitmap
)