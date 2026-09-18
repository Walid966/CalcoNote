package com.hesba.core

/**
 * محرك الحساب: منطق خالص بدون أي اعتماد على أندرويد حتى يمكن اختباره مباشرة.
 */
class CalculatorEngine(private val historyLimit: Int = 12) {

    var current: String = "0"
        private set
    var firstOperand: Double? = null
        private set
    var pendingOperator: String? = null
        private set
    var waitingForSecond: Boolean = false
        private set
    var expression: String = ""
        private set
    var lastResult: String? = null
        private set
    var lastError: String? = null
        private set
    var draftNoteLeft: String = ""
        private set
    var draftNoteRight: String = ""
        private set

    private val historyItems = mutableListOf<Operation>()
    private val ledgerIds = mutableListOf<Long>()
    private var lastId = 0L

    /** تنبيه للتطبيق حتى يحفظ السجل بعد كل تغيير. */
    var onHistoryChanged: (() -> Unit)? = null

    val history: List<Operation> get() = historyItems

    val ledgerOperationIds: List<Long> get() = ledgerIds.toList()

    val ledgerOperation: Operation? get() = ledgerIds.lastOrNull()?.let(::findHistory)

    val ledgerOperations: List<Operation> get() = ledgerIds.mapNotNull(::findHistory)

    val draftSide: Side get() = if (firstOperand == null) Side.LEFT else Side.RIGHT

    val canAddNote: Boolean
        get() = (current != "0" && !waitingForSecond) || (waitingForSecond && pendingOperator != null)

    val hasCurrentNumber: Boolean
        get() = firstOperand == null && !waitingForSecond && current != "0"

    val hasDraft: Boolean
        get() = firstOperand != null || draftNoteLeft.isNotEmpty() ||
            draftNoteRight.isNotEmpty() || hasCurrentNumber

    fun draftNote(side: Side): String = if (side == Side.LEFT) draftNoteLeft else draftNoteRight

    fun setDraftNote(side: Side, value: String) {
        if (side == Side.LEFT) draftNoteLeft = value else draftNoteRight = value
    }

    fun findHistory(id: Long): Operation? = historyItems.firstOrNull { it.id == id }

    fun replaceHistory(items: List<Operation>) {
        historyItems.clear()
        historyItems.addAll(items.take(historyLimit))
        ledgerIds.clear()
        historyItems.firstOrNull()?.let { ledgerIds.add(it.id) }
    }

    fun consumeError(): String? {
        val message = lastError
        lastError = null
        return message
    }

    fun snapshot(): SessionState = SessionState(
        current = current,
        firstOperand = firstOperand,
        pendingOperator = pendingOperator,
        waitingForSecond = waitingForSecond,
        expression = expression,
        leftNote = draftNoteLeft,
        rightNote = draftNoteRight,
        lastResult = lastResult
    )

    fun restore(state: SessionState) {
        current = state.current.ifEmpty { "0" }
        firstOperand = state.firstOperand
        pendingOperator = state.pendingOperator
        waitingForSecond = state.waitingForSecond
        expression = state.expression
        draftNoteLeft = state.leftNote
        draftNoteRight = state.rightNote
        lastResult = state.lastResult
    }

    fun reset() {
        current = "0"
        firstOperand = null
        pendingOperator = null
        waitingForSecond = false
        expression = ""
        draftNoteLeft = ""
        draftNoteRight = ""
        ledgerIds.clear()
        lastResult = null
        lastError = null
    }

    fun clearHistory() {
        historyItems.clear()
        ledgerIds.clear()
        onHistoryChanged?.invoke()
    }

    fun inputDigit(digit: Char) {
        if (startingNewCalculation()) startFreshDraft()
        current = if (waitingForSecond) {
            waitingForSecond = false
            digit.toString()
        } else if (current == "0") {
            digit.toString()
        } else {
            current + digit
        }
    }

    fun inputDecimal() {
        if (startingNewCalculation()) startFreshDraft()
        current = if (waitingForSecond) {
            waitingForSecond = false
            "0."
        } else if (current.contains(".")) {
            current
        } else {
            "$current."
        }
    }

    fun backspace() {
        current = if (current.length > 1) current.dropLast(1) else "0"
    }

    fun percent() {
        current = formatNumber((current.toDoubleOrNull() ?: 0.0) / 100)
    }

    /**
     * اختيار عملية؛ وقد تُنهي عملية سابقة عند الحساب المتسلسل.
     * @return العملية المحفوظة حديثًا إن وُجدت.
     */
    fun chooseOperator(next: String): Operation? {
        val currentValue = current.toDoubleOrNull() ?: 0.0
        if (pendingOperator != null && waitingForSecond) {
            pendingOperator = next
            expression = "${formatNumber(firstOperand ?: 0.0)} ${operatorSymbol(next)}"
            return null
        }
        var committed: Operation? = null
        if (firstOperand == null) {
            firstOperand = currentValue
        } else if (pendingOperator != null) {
            val operation = commitOperation(firstOperand!!, currentValue, pendingOperator!!) ?: return null
            current = operation.result
            firstOperand = operation.result.toDoubleOrNull() ?: 0.0
            draftNoteLeft = ""
            draftNoteRight = ""
            committed = operation
        }
        pendingOperator = next
        waitingForSecond = true
        expression = "${formatNumber(firstOperand ?: 0.0)} ${operatorSymbol(next)}"
        return committed
    }

    fun equalsNow(): Operation? {
        val activeOperator = pendingOperator ?: return null
        val left = firstOperand ?: return null
        val right = current.toDoubleOrNull() ?: 0.0
        val operation = commitOperation(left, right, activeOperator) ?: return null
        current = operation.result
        expression = "${operation.left} ${operatorSymbol(operation.pendingOperator)} ${operation.right} ="
        firstOperand = null
        pendingOperator = null
        waitingForSecond = true
        draftNoteLeft = ""
        draftNoteRight = ""
        return operation
    }

    fun updateNote(id: Long, side: Side, note: String): Boolean {
        val index = historyItems.indexOfFirst { it.id == id }
        if (index < 0) return false
        historyItems[index] = historyItems[index].withNote(side, note.trim())
        onHistoryChanged?.invoke()
        return true
    }

    private fun startingNewCalculation(): Boolean =
        firstOperand == null && pendingOperator == null && (waitingForSecond || current == "0")

    private fun startFreshDraft() {
        ledgerIds.clear()
        draftNoteLeft = ""
        draftNoteRight = ""
        expression = ""
    }

    private fun commitOperation(left: Double, right: Double, activeOperator: String): Operation? {
        val result = calculate(left, right, activeOperator)
        if (!result.isFinite()) {
            lastError = "لا يمكن القسمة على صفر"
            return null
        }
        val operation = Operation(
            id = nextId(),
            left = formatNumber(left),
            right = formatNumber(right),
            pendingOperator = activeOperator,
            result = formatNumber(result),
            leftNote = draftNoteLeft,
            rightNote = draftNoteRight
        )
        historyItems.add(0, operation)
        while (historyItems.size > historyLimit) {
            historyItems.removeAt(historyItems.size - 1)
        }
        ledgerIds.add(operation.id)
        lastResult = operation.result
        lastError = null
        onHistoryChanged?.invoke()
        return operation
    }

    private fun nextId(): Long {
        val now = System.currentTimeMillis()
        lastId = if (now > lastId) now else lastId + 1
        return lastId
    }

    private fun calculate(left: Double, right: Double, activeOperator: String): Double =
        when (activeOperator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            "/" -> if (right == 0.0) Double.NaN else left / right
            else -> right
        }
}
