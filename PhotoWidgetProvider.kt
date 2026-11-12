package com.example.photowidgetstudio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.RemoteViews
import kotlin.math.min

class PhotoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.photo_widget)
            val prefs = PhotoStore.prefs(context)
            val photoId = prefs.getString("widget_$id", null)
            if (photoId != null) {
                val file = PhotoStore.photoFile(context, photoId)
                if (file.exists()) {
                    val raw = BitmapFactory.decodeFile(file.absolutePath)
                    if (raw != null) {
                        val radius = prefs.getInt("rounding_$photoId", 12).coerceIn(0, 40) / 100f
                        views.setImageViewBitmap(R.id.widget_image, rounded(raw, radius))
                    }
                }
            }
            val launch = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, id, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_image, pending)
            manager.updateAppWidget(id, views)
        }

        private fun rounded(src: Bitmap, fraction: Float): Bitmap {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val path = Path()
            val radius = min(src.width, src.height) * fraction
            path.addRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()),
                radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(src, 0f, 0f, paint)
            canvas.restore()
            return out
        }
    }
}
