package com.ibala.tvwall

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

// Port of Typewall's challenge: retype a random passage exactly. The first
// wrong character, a backspace, or a paste clears the input and swaps in a
// different passage. Only typing characters at the end is accepted.
class Challenge(
    context: Context,
    private val passageView: TextView,
    private val input: EditText,
    private val statusView: TextView,
    private val onProgress: () -> Unit,
    private val onSuccess: () -> Unit,
) {
    private val passages = loadPassages(context)
    private var target = pick(null)
    private var previous = ""
    private var resetting = false
    private var done = false

    private val green = context.getColor(R.color.good)
    private val muted = context.getColor(R.color.muted)
    private val accent = context.getColor(R.color.accent)
    private val bad = context.getColor(R.color.bad)

    init {
        input.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        input.isLongClickable = false
        input.setTextIsSelectable(false)
        input.customSelectionActionModeCallback = NoActions
        input.customInsertionActionModeCallback = NoActions
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) = check(s.toString())
        })
        render()
    }

    private fun check(v: String) {
        if (resetting || done) return
        // Anything other than appending to what was there (backspace, paste,
        // autocorrect rewriting a word) counts as a mistake, as does a typo.
        val appended = v.length > previous.length && v.startsWith(previous)
        if (!appended || !target.startsWith(v)) return fail()
        previous = v
        statusView.setTextColor(muted)
        render()
        onProgress()
        if (v == target) {
            done = true
            input.isEnabled = false
            statusView.setTextColor(green)
            statusView.text = "Correct."
            onSuccess()
        }
    }

    private fun fail() {
        target = pick(target)
        previous = ""
        resetting = true
        input.setText("")
        resetting = false
        render()
        statusView.setTextColor(bad)
        statusView.text = "Wrong character. New passage. Start again."
        passageView.animate().translationX(12f).setDuration(50).withEndAction {
            passageView.animate().translationX(0f).setDuration(50)
        }
        onProgress()
    }

    private fun render() {
        val typed = previous.length
        val s = SpannableString(target)
        s.setSpan(ForegroundColorSpan(muted), 0, target.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (typed > 0) s.setSpan(ForegroundColorSpan(green), 0, typed, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (typed < target.length) {
            s.setSpan(ForegroundColorSpan(Color.WHITE), typed, typed + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(BackgroundColorSpan(accent and 0x55FFFFFF), typed, typed + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        passageView.text = s
        val total = target.split(' ').size
        val words = previous.split(' ').size - 1
        statusView.text = "$words / $total words"
    }

    private fun pick(exclude: String?): String =
        passages.filter { it != exclude }.random()

    private object NoActions : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
        override fun onDestroyActionMode(mode: ActionMode?) {}
    }

    companion object {
        fun loadPassages(context: Context): List<String> =
            context.assets.open("passages.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }
}

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}
