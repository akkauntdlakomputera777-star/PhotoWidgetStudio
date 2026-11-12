package com.example.photowidgetstudio

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RectF
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class MainActivity : Activity() {
    private var source: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var brightness: SeekBar
    private lateinit var contrast: SeekBar
    private lateinit var rounding: SeekBar
    private lateinit var shape: Spinner
    private lateinit var status: TextView
    private val pickCode = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PhotoStore.migrateLegacy(this)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.rgb(247, 247, 247))
        }
        val scroller = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "Фото-виджет"
            textSize = 26f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        content.addView(title, lp(-1, -2))
        content.addView(TextView(this).apply {
            text = "Каждая сохранённая фотография хранится отдельно. При добавлении нового виджета выбери, какое фото на нём показывать."
            textSize = 14f; setTextColor(Color.DKGRAY); gravity = Gravity.CENTER
        }, lp(-1, -2).apply { bottomMargin = 12 })
        preview = ImageView(this).apply {
            setBackgroundColor(Color.LTGRAY)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Предпросмотр фотографии"
        }
        content.addView(preview, LinearLayout.LayoutParams(-1, dp(250)).apply { bottomMargin = dp(12) })
        val pick = Button(this).apply { text = "Выбрать фотографию"; setOnClickListener { pickPhoto() } }
        content.addView(pick, lp(-1, -2))
        content.addView(label("Форма виджета"))
        shape = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Квадратная", "Вертикальная", "Горизонтальная"))
        }
        content.addView(shape, lp(-1, -2))
        brightness = addSlider(content, "Яркость", 100, 100)
        contrast = addSlider(content, "Контраст", 100, 100)
        rounding = addSlider(content, "Скругление углов", 40, 12)
        val save = Button(this).apply {
            text = "Сохранить фото для виджета"
            setOnClickListener { saveForWidget() }
        }
        content.addView(save, lp(-1, -2).apply { topMargin = dp(12) })
        val how = Button(this).apply {
            text = "Как добавить на главный экран"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Добавление виджета")
                    .setMessage("1. Сохрани одну или несколько фотографий в приложении.\n2. Вернись на главный экран телефона и удерживай пустое место.\n3. Открой «Виджеты».\n4. Найди «Фото-виджет» и перетащи его на экран.\n5. В появившемся окне выбери фотографию для этого экземпляра виджета.\n6. Чтобы добавить ещё один виджет, повтори шаги 2–5 и выбери другое фото. Уже размещённые виджеты сохранят свои фотографии.")
                    .setPositiveButton("Понятно", null).show()
            }
        }
        content.addView(how, lp(-1, -2))
        status = TextView(this).apply {
            text = "Сначала выбери фотографию."
            textSize = 14f; setTextColor(Color.DKGRAY); gravity = Gravity.CENTER
        }
        content.addView(status, lp(-1, -2).apply { topMargin = dp(8) })
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun label(s: String) = TextView(this).apply {
        text = s; textSize = 16f; setTextColor(Color.BLACK); setPadding(0, dp(8), 0, 0)
    }
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addSlider(parent: LinearLayout, title: String, max: Int, initial: Int): SeekBar {
        parent.addView(label(title))
        val bar = SeekBar(this).apply { this.max = max; progress = initial }
        parent.addView(bar, lp(-1, -2))
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { renderPreview() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        return bar
    }

    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, pickCode)
    }

    @Deprecated("Legacy result callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            contentResolver.openInputStream(uri)?.use { source = BitmapFactory.decodeStream(it) }
            renderPreview()
            status.text = "Фото загружено. Настрой его и нажми «Сохранить»."
        }
    }

    private fun adjustedBitmap(): Bitmap? {
        val original = source ?: return null
        val b = brightness.progress / 100f
        val c = contrast.progress / 100f
        val scale = c
        val translate = (1f - c) * 128f + (b - 1f) * 100f
        val matrix = ColorMatrix(floatArrayOf(
            scale,0f,0f,0f,translate, 0f,scale,0f,0f,translate,
            0f,0f,scale,0f,translate, 0f,0f,0f,1f,0f
        ))
        val out = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(original, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return out
    }

    private fun renderPreview() {
        val bmp = adjustedBitmap() ?: return
        preview.setImageBitmap(bmp)
    }

    private fun saveForWidget() {
        val bmp = adjustedBitmap()
        if (bmp == null) {
            Toast.makeText(this, "Сначала выбери фотографию", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val photoId = PhotoStore.save(this, bmp, rounding.progress, shape.selectedItem.toString())
            val count = PhotoStore.allIds(this).size
            status.text = "Фото сохранено отдельно (всего: $count). Уже добавленные виджеты не изменены. Чтобы создать новый: добавь «Фото-виджет» на главный экран и выбери эту фотографию."
            Toast.makeText(this, "Новая фотография сохранена", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            status.text = "Не удалось сохранить фото: ${e.message}"
        }
    }
}
