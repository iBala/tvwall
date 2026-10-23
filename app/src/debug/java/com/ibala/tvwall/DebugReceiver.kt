package com.ibala.tvwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

// Debug builds only: lets adb change today's usage and rules to test
// enforcement quickly:
//   adb shell am broadcast -n com.ibala.tvwall/.DebugReceiver --el used_ms 7200000
//   adb shell am broadcast -n com.ibala.tvwall/.DebugReceiver --ei curfew_weekend 1230
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        if (intent.hasExtra("used_ms")) store.addUsage(intent.getLongExtra("used_ms", 0) - store.usedMs)
        for (day in Store.Day.entries) {
            val extra = "curfew_" + day.key.lowercase()
            if (intent.hasExtra(extra)) {
                store.setRules(day, store.rules(day).copy(curfewMinute = intent.getIntExtra(extra, 0)))
            }
        }
        Log.i(
            "TvWall",
            "used=${store.usedMs} remaining=${store.remainingMs} blocked=${store.isBlocked} " +
                "curfew=${store.isCurfew} " + Store.Day.entries.joinToString(" ") { "${it.key}=${store.rules(it)}" }
        )
    }
}
