package com.example.photowidgetstudio

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Android launches this screen separately for every newly placed widget instance. */
class WidgetConfigActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        PhotoStore.migrateLegacy(this)
        showPhotoPicker()
    }

    private fun showPhotoPicker() {
        val ids = PhotoStore.allIds(this)
        if (ids.isEmpty()) {
            Toast.makeText(this, "Сначала открой приложение и сохрани фотографию", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val labels = ids.map { PhotoStore.displayName(this, it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Выбери фото для нового виджета")
            .setItems(labels) { _, which ->
                val photoId = ids[which]
                PhotoStore.prefs(this).edit().putString("widget_$appWidgetId", photoId).apply()
                val manager = AppWidgetManager.getInstance(this)
                PhotoWidgetProvider.updateWidget(this, manager, appWidgetId)
                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, result)
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
