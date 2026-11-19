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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.jalthouse.pausephone.data.Store
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Each morning: "Would you like to set an intention for today?" You can answer in the
 * notification itself, or tap it to choose in the app.
 */
object DailyIntention {
    private const val CHANNEL = "intention"
    private const val ID = 21
    const val KEY = "intention"
    const val ACTION_ASK = "com.jalthouse.pausephone.ASK_INTENTION"
    const val ACTION_SET = "com.jalthouse.pausephone.SET_INTENTION"
    const val EXTRA_OPEN = "openIntention"

    private fun askIntent(context: Context) = PendingIntent.getBroadcast(
        context, ID,
        Intent(context, IntentionReceiver::class.java).setAction(ACTION_ASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets or cancels the daily alarm to match the setting. Safe to call often. */
    fun schedule(context: Context) {
        Store.init(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = askIntent(context)
        if (!Store.intentionReminder) {
            alarms.cancel(pi)
            return
        }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(LocalTime.of(Store.intentionTime / 60, Store.intentionTime % 60))
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, millis, AlarmManager.INTERVAL_DAY, pi)
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Morning intention", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A question each morning: would you like to set an intention for today?"
            },
        )
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, ID + 1,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun ask(context: Context) {
        Store.init(context)
        if (!Store.intentionReminder || Store.todayIntention() != null || !canNotify(context)) return
        channel(context)
        val reply = PendingIntent.getBroadcast(
            context, ID + 2,
            Intent(context, IntentionReceiver::class.java).setAction(ACTION_SET),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
        val input = RemoteInput.Builder(KEY)
            .setLabel("Today, I'd like…")
            .setChoices(Store.DAY_INTENTIONS.take(4).toTypedArray())
            .build()
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_pause, "Set an intention", reply)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pause)
            .setContentTitle("Good morning")
            .setContentText("Would you like to set an intention for today?")
            .setContentIntent(openApp(context))
            .addAction(action)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }

    /** After an answer in the notification: a quiet confirmation that goes away by itself. */
    fun confirm(context: Context, text: String) {
        if (!canNotify(context)) return
        channel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pause)
            .setContentTitle("Today: $text")
            .setContentText("[pause] will remind you of it before your apps.")
            .setContentIntent(openApp(context))
            .setTimeoutAfter(6_000)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }
}

class IntentionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        when (intent.action) {
            DailyIntention.ACTION_ASK -> DailyIntention.ask(context)
            DailyIntention.ACTION_SET -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(DailyIntention.KEY)?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    Store.setTodayIntention(text.lowercase())
                    DailyIntention.confirm(context, text.lowercase())
                }
            }
        }
    }
}
