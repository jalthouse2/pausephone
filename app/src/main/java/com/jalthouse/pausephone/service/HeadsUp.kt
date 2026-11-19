package com.jalthouse.pausephone.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jalthouse.pausephone.R

/** A quiet notice and a soft tap shortly before a session ends. */
object HeadsUp {
    private const val CHANNEL = "heads_up"
    private const val ID = 11

    fun post(context: Context, label: String, minutes: Int) {
        context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }
            ?.vibrate(VibrationEffect.createOneShot(40, 60))
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Before a session ends", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                description = "A quiet heads-up shortly before your time in an app is up"
            },
        )
        val text = if (minutes == 1) "One minute left with $label." else "$minutes minutes left with $label."
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Almost time")
            .setContentText(text)
            .setTimeoutAfter(minutes * 60_000L)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }
}
