package com.ibala.tvwall

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

// Bound by the system as an accessibility service, so it starts at boot, stays
// alive, may launch the block screen from the background, and can put the TV
// in standby with GLOBAL_ACTION_LOCK_SCREEN.
//
// Every few seconds it adds screen-on time to today's usage. Once the budget
// is spent, or curfew starts, it keeps the block screen in front until a passage is typed.
class WatchService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var power: PowerManager
    private var lastTick = 0L
    // The screensaver keeps the TV "interactive", but nobody is watching.
    private var dreaming = false

    private val dreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            dreaming = intent.action == Intent.ACTION_DREAMING_STARTED
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, if (store.isBlocked) LOCKED_TICK_MS else TICK_MS)
        }
    }

    override fun onServiceConnected() {
        instance = this
        store = Store(this)
        power = getSystemService(PowerManager::class.java)
        lastTick = SystemClock.elapsedRealtime()
        registerReceiver(dreamReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        })
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (::store.isInitialized) unregisterReceiver(dreamReceiver)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // An app or the launcher came to the front: re-check right away rather
        // than waiting for the next tick.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) enforce()
    }

    override fun onInterrupt() {}

    private fun onTick() {
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastTick
        lastTick = now
        // Only count time while the screen is on and showing TV: not the
        // screensaver, and not the block screen. A long gap means the process
        // was frozen or the TV was in standby, so it doesn't count.
        if (power.isInteractive && !dreaming && !store.isBlocked && delta <= TICK_MS * 3) store.addUsage(delta)
        warn()
        enforce()
    }

    private fun warn() {
        if (!power.isInteractive || store.isBlocked) return
        val left = store.remainingMs
        // Show only the closest warning; mark the earlier ones as shown so
        // they don't pop up afterwards.
        val due = WARN_AT_MINUTES.filter { left <= it * 60_000L }
        val fresh = due.filter { store.once("warn:${store.warnEpoch}:$it") }
        if (due.isNotEmpty() && due.first() in fresh) {
            Toast.makeText(this, "TV Wall: ${due.first()} min of TV left", Toast.LENGTH_LONG).show()
        }
    }

    private fun enforce() {
        if (!::store.isInitialized || !power.isInteractive || !store.isBlocked) return
        if (BlockActivity.isShowing) return
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    fun sleepTv() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    companion object {
        private const val TICK_MS = 5_000L
        private const val LOCKED_TICK_MS = 1_000L
        private val WARN_AT_MINUTES = listOf(1, 5)

        // Set while the service is connected; the block screen uses it to put
        // the TV in standby.
        var instance: WatchService? = null
            private set
    }
}
