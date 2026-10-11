package com.ibala.tvwall

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// All state lives in one SharedPreferences file. Usage is per calendar day:
// the first read on a new day wipes yesterday's counters.
//
// Rules are kept separately for weekdays (Mon-Fri) and weekends (Sat, Sun):
//  - a daily allowance of screen-on time,
//  - how much extra time a typed passage buys,
//  - an optional curfew: no TV from that time until CURFEW_END the next morning.
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tvwall", Context.MODE_PRIVATE)

    fun rules(weekend: Boolean) = Rules(
        limitMinutes = prefs.getInt(key(KEY_LIMIT, weekend), if (weekend) 300 else 72),
        unlockMinutes = prefs.getInt(key(KEY_UNLOCK, weekend), if (weekend) 60 else 15),
        curfewMinute = prefs.getInt(key(KEY_CURFEW, weekend), if (weekend) NO_CURFEW else 20 * 60),
    )

    fun setRules(weekend: Boolean, r: Rules) {
        prefs.edit()
            .putInt(key(KEY_LIMIT, weekend), r.limitMinutes)
            .putInt(key(KEY_UNLOCK, weekend), r.unlockMinutes)
            .putInt(key(KEY_CURFEW, weekend), r.curfewMinute)
            .apply()
    }

    // Today's rules.
    val today: Rules get() = rules(isWeekend(LocalDate.now()))

    // Screen-on time today, in milliseconds.
    val usedMs: Long
        get() = rollover().getLong(KEY_USED, 0)

    // A typed passage pushes today's budget out to this much used time...
    private val unlockedUntilMs: Long
        get() = rollover().getLong(KEY_UNLOCKED_UNTIL, 0)

    // ...or, during curfew, allows TV until this wall-clock time.
    private val curfewPassUntil: Long
        get() = prefs.getLong(KEY_CURFEW_PASS, 0)

    private val budgetMs: Long
        get() = maxOf(today.limitMinutes * 60_000L, unlockedUntilMs)

    private val budgetRemainingMs: Long
        get() = budgetMs - usedMs

    // Time until curfew stops the TV: 0 while in curfew without a pass,
    // Long.MAX_VALUE if no curfew is coming before the day ends.
    private val curfewRemainingMs: Long
        get() {
            val now = System.currentTimeMillis()
            curfewWindowAt(LocalDateTime.now())?.let { (_, end) ->
                return if (curfewPassUntil > now) minOf(curfewPassUntil, end) - now else 0
            }
            val start = todayCurfewStart() ?: return Long.MAX_VALUE
            return if (start > now) start - now else Long.MAX_VALUE
        }

    val remainingMs: Long
        get() = minOf(budgetRemainingMs, curfewRemainingMs)

    val isBlocked: Boolean
        get() = remainingMs <= 0

    val isCurfew: Boolean
        get() = curfewRemainingMs <= 0

    // Key that changes whenever the budget or curfew pass moves, so warnings
    // fire again after an unlock.
    val warnEpoch: String
        get() = "$budgetMs:$curfewPassUntil"

    fun addUsage(ms: Long) {
        rollover().edit().putLong(KEY_USED, usedMs + ms).apply()
    }

    // Lifts whichever limit is blocking, by today's unlock length.
    fun grantUnlock() {
        val unlockMs = today.unlockMinutes * 60_000L
        val edit = rollover().edit()
        if (budgetRemainingMs <= 0) edit.putLong(KEY_UNLOCKED_UNTIL, usedMs + unlockMs)
        if (curfewRemainingMs <= 0) edit.putLong(KEY_CURFEW_PASS, System.currentTimeMillis() + unlockMs)
        edit.apply()
    }

    // Returns true the first time it's called for a given key today.
    fun once(key: String): Boolean {
        val set = rollover().getStringSet(KEY_ONCE, emptySet())!!
        if (key in set) return false
        prefs.edit().putStringSet(KEY_ONCE, set + key).apply()
        return true
    }

    private fun todayCurfewStart(): Long? {
        val date = LocalDate.now()
        val minute = rules(isWeekend(date)).curfewMinute
        if (minute == NO_CURFEW) return null
        return epochMs(date.atStartOfDay().plusMinutes(minute.toLong()))
    }

    // The curfew window (start, end in epoch ms) that contains `t`, if any.
    // A curfew set on a day runs from its start time until CURFEW_END the next
    // morning, so after midnight it's yesterday's rule that applies.
    private fun curfewWindowAt(t: LocalDateTime): Pair<Long, Long>? {
        for (date in listOf(t.toLocalDate(), t.toLocalDate().minusDays(1))) {
            val minute = rules(isWeekend(date)).curfewMinute
            if (minute == NO_CURFEW) continue
            val start = date.atStartOfDay().plusMinutes(minute.toLong())
            val end = date.plusDays(1).atStartOfDay().plusMinutes(CURFEW_END.toLong())
            if (!t.isBefore(start) && t.isBefore(end)) return epochMs(start) to epochMs(end)
        }
        return null
    }

    private fun epochMs(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun rollover(): android.content.SharedPreferences {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DAY, null) != today) {
            prefs.edit()
                .putString(KEY_DAY, today)
                .putLong(KEY_USED, 0)
                .putLong(KEY_UNLOCKED_UNTIL, 0)
                .putStringSet(KEY_ONCE, emptySet())
                .apply()
        }
        return prefs
    }

    private fun key(base: String, weekend: Boolean) = base + if (weekend) "Weekend" else "Weekday"

    data class Rules(val limitMinutes: Int, val unlockMinutes: Int, val curfewMinute: Int) {
        // Every field is ordered so that a bigger value is looser.
        fun isLooserThan(o: Rules) =
            limitMinutes > o.limitMinutes || unlockMinutes > o.unlockMinutes || curfewMinute > o.curfewMinute
    }

    companion object {
        private const val KEY_LIMIT = "limitMinutes"
        private const val KEY_UNLOCK = "unlockMinutes"
        private const val KEY_CURFEW = "curfewMinute"
        private const val KEY_CURFEW_PASS = "curfewPassUntil"
        private const val KEY_DAY = "day"
        private const val KEY_USED = "usedMs"
        private const val KEY_UNLOCKED_UNTIL = "unlockedUntilMs"
        private const val KEY_ONCE = "once"

        // Minute of the day the curfew ends, the morning after it starts.
        const val CURFEW_END = 6 * 60
        // Stored as midnight-or-later so "off" sorts as the loosest choice.
        const val NO_CURFEW = 24 * 60

        // 0.1 h steps up to 2 h, then 0.5 h steps up to 12 h.
        val LIMIT_STEPS = (5..20).map { it * 6 } + (5..24).map { it * 30 }
        val UNLOCK_STEPS = listOf(5, 10, 15, 30, 60)
        // 18:00 to 23:30 in half hours, then off.
        val CURFEW_STEPS = (36..47).map { it * 30 } + NO_CURFEW

        fun isWeekend(date: LocalDate) =
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = maxOf(ms, 0) / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${"%02d".format(m)}m" else "${m}m"
}

fun formatClock(minuteOfDay: Int): String =
    if (minuteOfDay == Store.NO_CURFEW) "Off"
    else "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
