package com.ibala.tvwall

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate

// Launcher screen: today's usage and the rules for weekdays and weekends. As in
// Typewall, making a rule stricter is instant; loosening it (more daily time,
// longer unlocks, a later or no curfew) means typing a passage first.
class SettingsActivity : Activity() {
    private lateinit var store: Store
    private val sections = listOf(Section(false, "Weekdays (Mon–Fri)"), Section(true, "Weekends (Sat & Sun)"))

    private inner class Section(val weekend: Boolean, val title: String) {
        lateinit var draft: Store.Rules
        lateinit var view: View

        fun bind(parent: LinearLayout) {
            view = layoutInflater.inflate(R.layout.rule_section, parent, false)
            parent.addView(view)
            view.findViewById<TextView>(R.id.heading).text = title
            stepper(R.id.limitDown, R.id.limitUp, Store.LIMIT_STEPS, { draft.limitMinutes }) {
                draft = draft.copy(limitMinutes = it)
            }
            stepper(R.id.unlockDown, R.id.unlockUp, Store.UNLOCK_STEPS, { draft.unlockMinutes }) {
                draft = draft.copy(unlockMinutes = it)
            }
            stepper(R.id.curfewDown, R.id.curfewUp, Store.CURFEW_STEPS, { draft.curfewMinute }) {
                draft = draft.copy(curfewMinute = it)
            }
        }

        fun render() {
            view.findViewById<TextView>(R.id.limit).text = formatDuration(draft.limitMinutes * 60_000L)
            view.findViewById<TextView>(R.id.unlock).text = "${draft.unlockMinutes} min"
            view.findViewById<TextView>(R.id.curfew).text = formatClock(draft.curfewMinute)
        }

        private fun stepper(down: Int, up: Int, steps: List<Int>, get: () -> Int, set: (Int) -> Unit) {
            view.findViewById<Button>(down).setOnClickListener {
                steps.lastOrNull { it < get() }?.let(set); renderAll()
            }
            view.findViewById<Button>(up).setOnClickListener {
                steps.firstOrNull { it > get() }?.let(set); renderAll()
            }
        }

        val saved get() = store.rules(weekend)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = Store(this)
        val rules = findViewById<LinearLayout>(R.id.rules)
        sections.forEach { it.bind(rules) }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.cancel).setOnClickListener { resetForm(); showForm() }
    }

    override fun onResume() {
        super.onResume()
        resetForm()
        showForm()
    }

    private fun resetForm() {
        sections.forEach { it.draft = it.saved }
        renderAll()
    }

    private fun renderAll() {
        val on = isServiceEnabled()
        findViewById<TextView>(R.id.service).apply {
            text = if (on) "● Watching the TV" else "● Not running. Turn on TV Wall in Settings → Accessibility."
            setTextColor(getColor(if (on) R.color.good else R.color.bad))
        }
        val day = if (Store.isWeekend(LocalDate.now())) "weekend" else "weekday"
        findViewById<TextView>(R.id.today).text =
            "Today is a $day  ·  Watched: ${formatDuration(store.usedMs)}  ·  " + when {
                store.isCurfew -> "Curfew"
                store.isBlocked -> "Time's up"
                else -> "Left: ${formatDuration(store.remainingMs)}"
            }
        sections.forEach { it.render() }

        findViewById<TextView>(R.id.note).text = when {
            isLooser() -> "This loosens a rule, so you'll type a passage to save it."
            sections.any { it.draft != it.saved } -> "Stricter rules save instantly."
            else -> ""
        }
    }

    private fun isLooser() = sections.any { it.draft.isLooserThan(it.saved) }

    private fun save() {
        if (isLooser()) showChallenge() else apply()
    }

    private fun apply() {
        sections.forEach { store.setRules(it.weekend, it.draft) }
        showForm()
        renderAll()
    }

    private fun showChallenge() {
        findViewById<View>(R.id.form).show(false)
        findViewById<View>(R.id.confirm).show(true)
        // A fresh view each time so an abandoned attempt doesn't carry over.
        val host = findViewById<FrameLayout>(R.id.challengeHost)
        host.removeAllViews()
        val view = layoutInflater.inflate(R.layout.challenge, host, true)
        Challenge(
            this,
            view.findViewById(R.id.passage),
            view.findViewById(R.id.typing),
            view.findViewById(R.id.status),
            onProgress = {},
            onSuccess = { host.postDelayed({ apply() }, 600) },
        )
        view.findViewById<View>(R.id.typing).requestFocus()
    }

    private fun showForm() {
        findViewById<FrameLayout>(R.id.challengeHost).removeAllViews()
        findViewById<View>(R.id.confirm).show(false)
        findViewById<View>(R.id.form).show(true)
        findViewById<View>(R.id.save).requestFocus()
    }

    private fun isServiceEnabled(): Boolean =
        getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
}
