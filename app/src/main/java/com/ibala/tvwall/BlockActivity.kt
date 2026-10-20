package com.ibala.tvwall

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

// Shown once today's TV time is used up, or during curfew. WatchService
// brings it back to the front whenever something else takes over. Typing the
// passage grants today's unlock minutes; doing nothing for IDLE_SECONDS puts
// the TV in standby.
class BlockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var countdown: TextView
    private lateinit var input: EditText
    private var secondsLeft = IDLE_SECONDS

    private val countdownTick = object : Runnable {
        override fun run() {
            secondsLeft--
            if (secondsLeft <= 0) {
                secondsLeft = IDLE_SECONDS
                WatchService.instance?.sleepTv()
            }
            renderCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)
        store = Store(this)
        countdown = findViewById(R.id.countdown)
        findViewById<Button>(R.id.off).setOnClickListener { WatchService.instance?.sleepTv() }

        input = findViewById(R.id.typing)
        Challenge(
            this,
            findViewById(R.id.passage),
            input,
            findViewById(R.id.status),
            onProgress = { secondsLeft = IDLE_SECONDS; renderCountdown() },
            onSuccess = {
                store.grantUnlock()
                handler.removeCallbacks(countdownTick)
                countdown.text = "Unlocked for ${store.today.unlockMinutes} minutes."
                handler.postDelayed({ finish() }, 800)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
        if (!store.isBlocked) return finish()
        renderReason()
        // Coming back from Home or standby can leave focus on the button, so
        // typing would go nowhere.
        input.requestFocus()
        input.post {
            getSystemService(InputMethodManager::class.java).showSoftInput(input, 0)
        }
        secondsLeft = IDLE_SECONDS
        renderCountdown()
        handler.removeCallbacks(countdownTick)
        handler.postDelayed(countdownTick, 1000)
    }

    override fun onPause() {
        isShowing = false
        handler.removeCallbacks(countdownTick)
        super.onPause()
    }

    // Back would just reveal whatever was underneath; WatchService would put
    // this screen straight back, so swallow it instead.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK || super.onKeyDown(keyCode, event)

    // Refreshed on every resume: the screen can stay open while the reason
    // changes, e.g. the daily limit runs out and then curfew starts.
    private fun renderReason() {
        val curfew = store.isCurfew
        findViewById<TextView>(R.id.title).text =
            if (curfew) "No TV until ${formatClock(Store.CURFEW_END)}" else "TV time is up for today"
        findViewById<TextView>(R.id.summary).text =
            (if (curfew) "It's past curfew. " else "You've watched ${formatDuration(store.usedMs)} today. ") +
                "Type the passage below exactly to get ${store.today.unlockMinutes} more minutes."
    }

    private fun renderCountdown() {
        countdown.text = "TV turns off in ${secondsLeft}s unless you keep typing."
    }

    companion object {
        private const val IDLE_SECONDS = 60

        @Volatile
        var isShowing = false
            private set
    }
}
