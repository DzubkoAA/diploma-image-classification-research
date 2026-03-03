package com.example.vkp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var textView: TextView
    private lateinit var resultIcon: ImageView
    private lateinit var interpreter: Interpreter

    private val PICK_IMAGE_REQUEST = 1
    private val inputSize = 224 // Размер входа модели MobileNetV2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        textView = findViewById(R.id.textView)
        resultIcon = findViewById(R.id.resultIcon)

        val pickImageButton = findViewById<Button>(R.id.pickImageButton)
        val cameraButton = findViewById<Button>(R.id.cameraButton)
        val shareButton = findViewById<Button>(R.id.shareButton)
        val historyButton = findViewById<Button>(R.id.historyButton)

        // Загружаем TFLite модель
        interpreter = Interpreter(loadModelFile("mobilenetv2_finetuned2.tflite"))

        pickImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        cameraButton.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        shareButton.setOnClickListener {
            val textToShare = textView.text.toString()
            if (textToShare.isNotBlank()) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, textToShare)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(shareIntent, "Поделиться результатом"))
            }
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val bitmap: Bitmap? = when {
                data.data != null -> { // Из галереи
                    val imageUri: Uri? = data.data
                    val inputStream = contentResolver.openInputStream(imageUri!!)
                    BitmapFactory.decodeStream(inputStream)
                }
                data.extras?.get("data") != null -> { // С камеры
                    data.extras?.get("data") as Bitmap
                }
                else -> null
            }

            bitmap?.let {
                imageView.setImageBitmap(it)
                val result = runModel(it)
                textView.text = "Распознанный транспорт: $result"
                resultIcon.setImageResource(getIconByLabel(result))
                saveToHistory(result, it)
            }
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun runModel(bitmap: Bitmap): String {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, false)

        // ByteBuffer для MobileNetV2
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = scaledBitmap.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                // MobileNetV2 preprocess_input → [-1;1]
                inputBuffer.putFloat(r * 2f - 1f)
                inputBuffer.putFloat(g * 2f - 1f)
                inputBuffer.putFloat(b * 2f - 1f)
            }
        }

        val output = Array(1) { FloatArray(NUM_CLASSES) }
        interpreter.run(inputBuffer, output)

        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        return labels.getOrElse(maxIndex) { "Неизвестно" }
    }

    private fun getIconByLabel(label: String): Int {
        return when (label) {
            "Легковой автомобиль" -> R.drawable.ic_car
            "Грузовик" -> R.drawable.ic_truck
            "Автобус" -> R.drawable.ic_bus
            "Спецтранспорт" -> R.drawable.ic_emergency
            "Мотоцикл" -> R.drawable.ic_motorcycle
            else -> R.drawable.ic_default
        }
    }

    private fun saveToHistory(label: String, bitmap: Bitmap) {
        val iconResId = getIconByLabel(label)
        HistoryActivity.historyList.add(RecognitionResult(label, iconResId, bitmap))
    }

    companion object {
        val labels = arrayOf("Автобус", "Легковой автомобиль", "Спецтранспорт", "Мотоцикл", "Грузовик")
        const val NUM_CLASSES = 5
    }
}
