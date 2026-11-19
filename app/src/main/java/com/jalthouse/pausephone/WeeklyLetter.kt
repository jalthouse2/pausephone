package com.jalthouse.pausephone

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jalthouse.pausephone.data.Progress
import com.jalthouse.pausephone.data.Store
import com.jalthouse.pausephone.data.Usage
import com.jalthouse.pausephone.data.weekLetter
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A short letter about your week, as a notification on Sunday mornings at 10:00. */
object WeeklyLetter {
    private const val CHANNEL = "letter"
    private const val ID = 7

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context,
        ID,
        Intent(context, LetterReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets or cancels the weekly alarm to match the setting. Safe to call often. */
    fun schedule(context: Context) {
        Store.init(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        if (!Store.letterOn) {
            alarms.cancel(pi)
            return
        }
        val now = LocalDateTime.now()
        var at = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(LocalTime.of(10, 0))
        if (!at.isAfter(now)) at = at.plusWeeks(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, millis, AlarmManager.INTERVAL_DAY * 7, pi)
    }

    fun post(context: Context) {
        Store.init(context)
        if (!Store.letterOn) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sunday letter", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A few calm sentences about your week"
            },
        )
        Progress.captureBaseline(context)
        val story = Progress.story(context, Store.rules())
        val text = listOfNotNull(weekLetter(Store.rules(), Usage.weekSentence(context)), story).joinToString(" ")
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Your week")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }
}

/** Posts the letter when the alarm fires, and sets the alarm again after a restart. */
class LetterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> WeeklyLetter.schedule(context)
            else -> WeeklyLetter.post(context)
        }
    }
}
