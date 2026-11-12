package com.example.photowidgetstudio

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Stores each photo separately and keeps a stable photoId -> file mapping. */
object PhotoStore {
    private const val PREFS = "photo_widget"
    private const val IDS = "photo_ids"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun photoDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }
    fun photoFile(context: Context, photoId: String) = File(photoDir(context), "$photoId.jpg")

    fun allIds(context: Context): List<String> =
        prefs(context).getStringSet(IDS, emptySet())?.toList()?.sorted() ?: emptyList()

    fun save(context: Context, bitmap: Bitmap, rounding: Int, shape: String): String {
        val id = UUID.randomUUID().toString()
        val file = photoFile(context, id)
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) { "Не удалось записать фотографию" }
        }
        val p = prefs(context)
        val ids = p.getStringSet(IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(id)
        p.edit()
            .putStringSet(IDS, ids)
            .putString("name_$id", "Фото ${ids.size}")
            .putInt("rounding_$id", rounding)
            .putString("shape_$id", shape)
            .apply()
        return id
    }

    /** One-time migration from the old single-photo version. */
    fun migrateLegacy(context: Context) {
        val p = prefs(context)
        if (allIds(context).isNotEmpty()) return
        val old = File(context.filesDir, "widget_photo.jpg")
        if (!old.exists()) return
        val id = "legacy"
        old.copyTo(photoFile(context, id), overwrite = true)
        p.edit()
            .putStringSet(IDS, setOf(id))
            .putString("name_$id", "Старая фотография")
            .putInt("rounding_$id", p.getInt("rounding", 12))
            .putString("shape_$id", p.getString("shape", "Квадратная"))
            .apply()
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(ComponentName(context, PhotoWidgetProvider::class.java))
        widgetIds.forEach { p.edit().putString("widget_$it", id).apply() }
    }

    fun displayName(context: Context, id: String): String =
        prefs(context).getString("name_$id", "Фотография") ?: "Фотография"
}
