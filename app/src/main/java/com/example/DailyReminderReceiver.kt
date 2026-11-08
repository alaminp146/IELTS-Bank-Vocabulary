package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class DailyReminderReceiver : BroadcastReceiver() {

  companion object {
    const val CHANNEL_ID = "sticky_vocab_reminder_channel"
    const val CHANNEL_NAME = "Daily Vocabulary Reminders"
    const val NOTIFICATION_ID = 9001
    const val EXTRA_PREVIEW = "extra_preview_words"
    const val EXTRA_HOUR = "extra_hour"
    const val EXTRA_MINUTE = "extra_minute"

    fun scheduleReminder(
      context: Context,
      hour: Int,
      minute: Int,
      previewWords: String
    ) {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
      val intent = Intent(context, DailyReminderReceiver::class.java).apply {
        putExtra(EXTRA_PREVIEW, previewWords)
        putExtra(EXTRA_HOUR, hour)
        putExtra(EXTRA_MINUTE, minute)
      }

      val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (before(Calendar.getInstance())) {
          add(Calendar.DAY_OF_YEAR, 1)
        }
      }

      try {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          calendar.timeInMillis,
          pendingIntent
        )
      } catch (e: Exception) {
        alarmManager.set(
          AlarmManager.RTC_WAKEUP,
          calendar.timeInMillis,
          pendingIntent
        )
      }
    }

    fun cancelReminder(context: Context) {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
      val intent = Intent(context, DailyReminderReceiver::class.java)
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      alarmManager.cancel(pendingIntent)
    }

    fun sendImmediateNotification(context: Context, title: String, message: String) {
      ensureNotificationChannel(context)
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

      notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Daily reminder notifications for your 5 vocabulary words"
          enableVibration(true)
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    ensureNotificationChannel(context)

    val preview = intent.getStringExtra(EXTRA_PREVIEW) ?: "Time for today's 5 words!"
    val title = "📌 StickyVocab 900 — Daily Study Reminder"
    val message = "Keep your 180-day streak alive! $preview"

    sendImmediateNotification(context, title, message)

    // Automatically reschedule for tomorrow at the same time
    val hour = intent.getIntExtra(EXTRA_HOUR, -1)
    val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
    if (hour in 0..23 && minute in 0..59) {
      scheduleReminder(context, hour, minute, preview)
    }
  }
}
