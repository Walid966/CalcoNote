package com.hesba.nativeapp

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.hesba.core.CalculatorEngine
import com.hesba.core.Operation
import com.hesba.core.SessionState
import com.hesba.core.Side
import com.hesba.core.formatNumber
import com.hesba.core.operatorSymbol
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** الواجهة الأصلية للتطبيق: حاسبة مع ملاحظات محفوظة على الجهاز. */
class MainActivity : Activity() {

    private lateinit var engine: CalculatorEngine
    private lateinit var store: Store

    private lateinit var expressionText: TextView
    private lateinit var displayValueText: TextView
    private lateinit var lastResultRow: View
    private lateinit var lastResultValue: TextView
    private lateinit var noteField: View
    private lateinit var noteLabel: TextView
    private lateinit var noteInput: EditText
    private lateinit var keypad: LinearLayout
    private lateinit var draftLedger: LinearLayout
    private lateinit var historyPanel: View
    private lateinit var historyList: LinearLayout
    private lateinit var historyCount: TextView

    private var bindingNote = false
    private var noteDialog: Dialog? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("ar", "EG"))

    private class KeySpec(
        val label: String,
        val tag: String,
        val styleRes: Int,
        val weight: Float = 1f
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        expressionText = findViewById(R.id.expressionText)
        displayValueText = findViewById(R.id.displayValueText)
        lastResultRow = findViewById(R.id.lastResultRow)
        lastResultValue = findViewById(R.id.lastResultValue)
        noteField = findViewById(R.id.noteField)
        noteLabel = findViewById(R.id.noteLabel)
        noteInput = findViewById(R.id.noteInput)
        keypad = findViewById(R.id.keypad)
        draftLedger = findViewById(R.id.draftLedger)
        historyPanel = findViewById(R.id.historyPanel)
        historyList = findViewById(R.id.historyList)
        historyCount = findViewById(R.id.historyCount)

        engine = CalculatorEngine()
        store = Store(this)
        engine.replaceHistory(store.loadHistory())
        store.loadSession()?.let { engine.restore(it) }
        engine.onHistoryChanged = { store.saveHistory(engine.history) }

        buildKeypad()
        bindActions()
        render()
    }

    private fun buildKeypad() {
        val rows = listOf(
            listOf(
                KeySpec("AC", ACTION_CLEAR, R.style.KeyButton_Muted),
                KeySpec("⌫", ACTION_BACKSPACE, R.style.KeyButton_Muted),
                KeySpec("%", ACTION_PERCENT, R.style.KeyButton_Muted),
                KeySpec("÷", "/", R.style.KeyButton_Operator)
            ),
            listOf(
                KeySpec("7", "7", R.style.KeyButton),
                KeySpec("8", "8", R.style.KeyButton),
                KeySpec("9", "9", R.style.KeyButton),
                KeySpec("×", "*", R.style.KeyButton_Operator)
            ),
            listOf(
                KeySpec("4", "4", R.style.KeyButton),
                KeySpec("5", "5", R.style.KeyButton),
                KeySpec("6", "6", R.style.KeyButton),
                KeySpec("−", "-", R.style.KeyButton_Operator)
            ),
            listOf(
                KeySpec("1", "1", R.style.KeyButton),
                KeySpec("2", "2", R.style.KeyButton),
                KeySpec("3", "3", R.style.KeyButton),
                KeySpec("+", "+", R.style.KeyButton_Operator)
            ),
            listOf(
                KeySpec("0", "0", R.style.KeyButton, 2f),
                KeySpec(".", ACTION_DECIMAL, R.style.KeyButton),
                KeySpec("=", ACTION_EQUALS, R.style.KeyButton_Equals)
            )
        )

        keypad.removeAllViews()
        rows.forEachIndexed { index, specs ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) row.setPadding(0, dp(10), 0, 0)

            specs.forEach { spec ->
                val button = Button(this, null, 0, spec.styleRes)
                button.tag = spec.tag
                button.text = spec.label
                button.layoutParams = LinearLayout.LayoutParams(0, dp(54), spec.weight)
                button.setOnClickListener(keyClickListener)
                row.addView(button)
            }
            keypad.addView(row)
        }
    }

    private val keyClickListener = View.OnClickListener { view ->
        val tag = view.tag as? String
        if (tag != null) handleKey(tag)
    }

    private fun handleKey(tag: String) {
        when {
            tag == ACTION_CLEAR -> {
                engine.reset()
                render()
            }
            tag == ACTION_BACKSPACE -> {
                engine.backspace()
                render()
            }
            tag == ACTION_PERCENT -> {
                engine.percent()
                render()
            }
            tag == ACTION_DECIMAL -> {
                engine.inputDecimal()
                render()
            }
            tag == ACTION_EQUALS -> completeCalculation()
            tag in OPERATORS -> {
                engine.chooseOperator(tag)
                engine.consumeError()?.let { showToast(it) }
                render()
            }
            tag.length == 1 && tag[0].isDigit() -> {
                engine.inputDigit(tag[0])
                render()
            }
        }
    }

    private fun completeCalculation() {
        val operation = engine.equalsNow()
        if (operation == null) {
            engine.consumeError()?.let { showToast(it) }
            render()
            return
        }
        render()
        showToast(getString(R.string.toast_calculation_saved))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun textStyle(value: String, colorRes: Int, sizeSp: Float, bold: Boolean = false): TextView {
        val text = TextView(this)
        text.text = value
        text.setTextColor(getColor(colorRes))
        text.textSize = sizeSp
        if (bold) text.setTypeface(text.typeface, android.graphics.Typeface.BOLD)
        return text
    }

    private fun render() {
        expressionText.text = engine.expression.ifEmpty { getString(R.string.ready_placeholder) }
        displayValueText.text = engine.current

        lastResultRow.visibility = if (engine.lastResult == null) View.GONE else View.VISIBLE
        lastResultValue.text = engine.lastResult ?: "0"

        noteField.visibility = if (engine.canAddNote) View.VISIBLE else View.GONE
        val expectsNextNumber = engine.waitingForSecond
        noteLabel.text = getString(
            if (expectsNextNumber) R.string.note_field_next else R.string.note_field_current
        )
        noteInput.hint = getString(
            if (expectsNextNumber) R.string.note_hint_next else R.string.note_hint_current
        )

        val expectedNote = engine.draftNote(engine.draftSide)
        if (noteInput.text.toString() != expectedNote) {
            bindingNote = true
            noteInput.setText(expectedNote)
            bindingNote = false
        }

        renderLedger()
        renderHistory()
        store.saveSession(engine)
    }

    private fun renderLedger() {
        draftLedger.removeAllViews()
        val ledgerOperation = engine.ledgerOperation
        draftLedger.setBackgroundResource(
            if (ledgerOperation != null) R.drawable.bg_ledger_complete else R.drawable.bg_ledger
        )

        if (ledgerOperation != null) {
            val title = textStyle(
                getString(R.string.ledger_title),
                R.color.forest_deep,
                11f,
                bold = true
            )
            title.setPadding(0, 0, 0, dp(6))
            draftLedger.addView(title)

            val operations = engine.ledgerOperations
            val hasActiveNextNumber = engine.hasDraft && engine.firstOperand != null
            operations.forEachIndexed { index, operation ->
                val isLastOperation = index == operations.lastIndex
                val hideEquals = index < operations.lastIndex || (isLastOperation && hasActiveNextNumber)
                appendLedgerOperation(operation, hideEquals, index > 0)
            }

            if (hasActiveNextNumber) {
                draftLedger.addView(ledgerOperator(operatorSymbol(engine.pendingOperator ?: "")))
                if (!engine.waitingForSecond) {
                    draftLedger.addView(ledgerEntry(engine.current, engine.draftNoteRight, null))
                }
            }
            return
        }

        if (!engine.hasDraft) {
            draftLedger.addView(
                textStyle(getString(R.string.ledger_empty), R.color.muted, 12f)
            )
            return
        }

        val leftNumber = engine.firstOperand?.let { formatNumber(it) } ?: engine.current
        if (engine.firstOperand != null || engine.draftNoteLeft.isNotEmpty() || engine.hasCurrentNumber) {
            draftLedger.addView(ledgerEntry(leftNumber, engine.draftNoteLeft, null))
        }
        engine.pendingOperator?.let { draftLedger.addView(ledgerOperator(operatorSymbol(it))) }
        if (engine.firstOperand != null && !engine.waitingForSecond) {
            draftLedger.addView(ledgerEntry(engine.current, engine.draftNoteRight, null))
        }
    }

    private fun appendLedgerOperation(operation: Operation, hideEquals: Boolean, hideLeft: Boolean) {
        if (!hideLeft) {
            draftLedger.addView(ledgerEntry(operation.left, operation.leftNote, operation.id to Side.LEFT))
        }
        draftLedger.addView(ledgerOperator(operatorSymbol(operation.pendingOperator)))
        draftLedger.addView(ledgerEntry(operation.right, operation.rightNote, operation.id to Side.RIGHT))
        if (!hideEquals) {
            draftLedger.addView(ledgerOperator("="))
            draftLedger.addView(ledgerEntry(operation.result, null, null))
        }
    }

    private fun ledgerEntry(number: String, note: String?, ref: Pair<Long, Side>?): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        row.setPadding(0, dp(3), 0, dp(3))

        val numberChip = textStyle(number, R.color.forest_deep, 15f, bold = true)
        numberChip.background = getDrawable(R.drawable.bg_chip)
        numberChip.setPadding(dp(9), dp(4), dp(9), dp(4))
        numberChip.minWidth = dp(36)
        numberChip.gravity = Gravity.CENTER
        row.addView(numberChip)

        if (!note.isNullOrEmpty()) {
            val noteView = textStyle(note, R.color.draft_note_text, 12f)
            val noteParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            noteParams.marginStart = dp(7)
            noteView.layoutParams = noteParams
            row.addView(noteView)
        }

        if (ref != null) {
            val icon = textStyle("✎", R.color.coral, 13f)
            val iconParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            iconParams.marginStart = dp(6)
            icon.layoutParams = iconParams
            icon.contentDescription = getString(R.string.cd_add_note)
            icon.setOnClickListener { openNoteDialog(ref.first, ref.second) }
            row.addView(icon)
        }
        return row
    }

    private fun ledgerOperator(symbol: String): View {
        val text = textStyle(symbol, R.color.coral, 16f, bold = true)
        text.setPadding(0, dp(2), 0, dp(2))
        return text
    }

    private fun renderHistory() {
        historyCount.text = engine.history.size.toString()
        historyList.removeAllViews()

        if (engine.history.isEmpty()) {
            val empty = LinearLayout(this)
            empty.orientation = LinearLayout.VERTICAL
            empty.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            empty.setPadding(0, dp(18), 0, dp(18))

            val icon = textStyle("✦", R.color.coral, 18f)
            icon.gravity = Gravity.CENTER
            empty.addView(icon)
            empty.addView(
                textStyle(getString(R.string.history_empty_title), R.color.ink, 15f, bold = true)
            )
            empty.addView(textStyle(getString(R.string.history_empty_copy), R.color.muted, 12f))
            historyList.addView(empty)
            return
        }

        engine.history.forEach { historyList.addView(historyItem(it)) }
    }

    private fun historyItem(operation: Operation): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cardParams.bottomMargin = dp(10)
        card.layoutParams = cardParams
        card.background = getDrawable(R.drawable.bg_history_item)
        card.setPadding(dp(12), dp(10), dp(12), dp(10))

        val meta = LinearLayout(this)
        meta.orientation = LinearLayout.HORIZONTAL
        meta.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val time = textStyle(timeFormat.format(Date(operation.id)), R.color.muted, 11f)
        time.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        meta.addView(time)
        meta.addView(textStyle(getString(R.string.result_label), R.color.ink, 11f, bold = true))
        card.addView(meta)

        val equation = LinearLayout(this)
        equation.orientation = LinearLayout.HORIZONTAL
        equation.gravity = Gravity.CENTER_VERTICAL
        val equationParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        equationParams.topMargin = dp(8)
        equation.layoutParams = equationParams
        equation.addView(
            chip(operation.left, operation.leftNote.isNotEmpty(), operation.id to Side.LEFT)
        )
        equation.addView(operatorLabel(operatorSymbol(operation.pendingOperator)))
        equation.addView(
            chip(operation.right, operation.rightNote.isNotEmpty(), operation.id to Side.RIGHT)
        )
        equation.addView(operatorLabel("="))
        equation.addView(textStyle(operation.result, R.color.forest_deep, 16f, bold = true))
        card.addView(equation)

        val notes = listOfNotNull(
            operation.leftNote.takeIf { it.isNotEmpty() }?.let { operation.left to it },
            operation.rightNote.takeIf { it.isNotEmpty() }?.let { operation.right to it }
        )
        if (notes.isNotEmpty()) {
            val preview = LinearLayout(this)
            preview.orientation = LinearLayout.VERTICAL
            val previewParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            previewParams.topMargin = dp(8)
            preview.layoutParams = previewParams
            notes.forEach { (number, note) ->
                preview.addView(textStyle("$number  $note", R.color.draft_note_text, 12f))
            }
            card.addView(preview)
        }
        return card
    }

    private fun chip(value: String, hasNote: Boolean, ref: Pair<Long, Side>): Button {
        val button = Button(this, null, 0, R.style.ChipButton)
        button.text = if (hasNote) "$value ✎" else value
        button.setPadding(dp(8), dp(4), dp(8), dp(4))
        button.setOnClickListener {
            button.contentDescription = getString(R.string.cd_add_note)
            openNoteDialog(ref.first, ref.second)
        }
        return button
    }

    private fun operatorLabel(symbol: String): View {
        val text = textStyle(symbol, R.color.muted, 14f)
        text.setPadding(dp(6), 0, dp(6), 0)
        return text
    }

    private fun bindActions() {
        val rootScroll = findViewById<View>(R.id.rootScroll)

        findViewById<Button>(R.id.historyToggle).setOnClickListener {
            historyPanel.visibility = View.VISIBLE
            rootScroll.post { rootScroll.scrollTo(0, historyPanel.top) }
        }
        findViewById<Button>(R.id.closeHistory).setOnClickListener {
            historyPanel.visibility = View.GONE
        }
        findViewById<Button>(R.id.clearAll).setOnClickListener {
            engine.reset()
            render()
        }
        findViewById<Button>(R.id.clearHistory).setOnClickListener {
            if (engine.history.isEmpty()) return@setOnClickListener
            engine.clearHistory()
            render()
            showToast(getString(R.string.toast_history_cleared))
        }
        noteInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (bindingNote) return
                engine.setDraftNote(engine.draftSide, s?.toString().orEmpty())
                renderLedger()
                store.saveSession(engine)
            }
        })
    }

    private fun openNoteDialog(id: Long, side: Side) {
        val operation = engine.findHistory(id) ?: return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_note)
        dialog.window?.setBackgroundDrawable(
            ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

        val numberView = dialog.findViewById<TextView>(R.id.modalNumber)
        val input = dialog.findViewById<EditText>(R.id.modalNoteInput)
        numberView.text = if (side == Side.LEFT) operation.left else operation.right
        input.setText(operation.note(side))
        input.setSelection(input.text.length)

        dialog.findViewById<Button>(R.id.modalCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.modalSave).setOnClickListener {
            engine.updateNote(id, side, input.text.toString())
            render()
            showToast(getString(R.string.toast_note_saved))
            dialog.dismiss()
        }

        dialog.setOnDismissListener { noteDialog = null }
        noteDialog = dialog
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        input.requestFocus()
    }

    override fun onBackPressed() {
        if (historyPanel.visibility == View.VISIBLE) {
            historyPanel.visibility = View.GONE
            return
        }
        super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (noteDialog != null) return super.onKeyDown(keyCode, event)
        if (noteInput.hasFocus()) return super.onKeyDown(keyCode, event)
        val tag = keyToTag(keyCode) ?: return super.onKeyDown(keyCode, event)
        handleKey(tag)
        return true
    }

    private fun keyToTag(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> "0"
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> "1"
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> "2"
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> "3"
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> "4"
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> "5"
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> "6"
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> "7"
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> "8"
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> "9"
        KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_NUMPAD_DOT -> ACTION_DECIMAL
        KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> "+"
        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> "-"
        KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "*"
        KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_NUMPAD_DIVIDE -> "/"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_EQUALS -> ACTION_EQUALS
        KeyEvent.KEYCODE_ESCAPE -> ACTION_CLEAR
        KeyEvent.KEYCODE_DEL -> ACTION_BACKSPACE
        else -> null
    }

    override fun onPause() {
        super.onPause()
        store.saveSession(engine)
    }

    private companion object {
        const val ACTION_CLEAR = "clear"
        const val ACTION_BACKSPACE = "backspace"
        const val ACTION_PERCENT = "percent"
        const val ACTION_DECIMAL = "decimal"
        const val ACTION_EQUALS = "equals"
        val OPERATORS = setOf("+", "-", "*", "/")
    }
}