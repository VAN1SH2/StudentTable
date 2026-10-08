package ru.sfu.student.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.sfu.student.StudentApp

/** Recalculate local lesson times and reminder delays after device clock changes. */
class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            (context.applicationContext as StudentApp).reminders.onDeviceClockChanged()
        }
    }
}