package com.hesba.core

private var failures = 0
private var checks = 0

private fun check(name: String, condition: Boolean, detail: String = "") {
    checks++
    if (condition) {
        println("PASS  $name")
    } else {
        failures++
        println("FAIL  $name ${if (detail.isEmpty()) "" else "-> $detail"}")
    }
}

private fun checkEquals(name: String, expected: Any?, actual: Any?) {
    check(name, expected == actual, "expected=$expected actual=$actual")
}

private fun engineWithNotes(leftNote: String, rightNote: String): CalculatorEngine {
    val engine = CalculatorEngine()
    engine.setDraftNote(Side.LEFT, leftNote)
    engine.setDraftNote(Side.RIGHT, rightNote)
    return engine
}

fun main() {
    checkEquals("default display", "0", CalculatorEngine().current)
    checkEquals("format integer", "12", formatNumber(12.0))
    checkEquals("format fraction", "0.3", formatNumber(0.1 + 0.2))
    checkEquals("format negative", "-2.5", formatNumber(-2.5))
    checkEquals("format infinity", "خطأ", formatNumber(Double.NaN))

    val addition = CalculatorEngine()
    addition.inputDigit('7')
    addition.chooseOperator("+")
    addition.inputDigit('5')
    val additionResult = addition.equalsNow()
    checkEquals("addition result", "12", additionResult?.result)
    checkEquals("addition display", "12", addition.current)
    checkEquals("addition expression", "7 + 5 =", addition.expression)
    checkEquals("addition saved", 1, addition.history.size)
    check("addition ready for new number", addition.waitingForSecond)

    val division = CalculatorEngine()
    division.inputDigit('9')
    division.chooseOperator("/")
    division.inputDigit('4')
    checkEquals("division result", "2.25", division.equalsNow()?.result)

    val zeroDivision = CalculatorEngine()
    zeroDivision.inputDigit('8')
    zeroDivision.chooseOperator("/")
    zeroDivision.inputDigit('0')
    checkEquals("divide by zero blocked", null, zeroDivision.equalsNow())
    checkEquals("divide by zero message", "لا يمكن القسمة على صفر", zeroDivision.lastError)
    checkEquals("divide by zero keeps history empty", 0, zeroDivision.history.size)

    val chained = CalculatorEngine()
    chained.inputDigit('2')
    chained.chooseOperator("+")
    chained.inputDigit('3')
    chained.chooseOperator("+")
    checkEquals("chain first result", "5", chained.current)
    checkEquals("chain first saved", 1, chained.history.size)
    chained.inputDigit('4')
    checkEquals("chain final result", "9", chained.equalsNow()?.result)
    checkEquals("chain saves both operations", 2, chained.history.size)
    checkEquals("chain ledger keeps both numbers", 2, chained.ledgerOperations.size)

    val swap = CalculatorEngine()
    swap.inputDigit('6')
    swap.chooseOperator("*")
    swap.chooseOperator("/")
    checkEquals("pendingOperator swapped", "/", swap.pendingOperator)
    checkEquals("swap does not save", 0, swap.history.size)

    val percentEngine = CalculatorEngine()
    percentEngine.inputDigit('5')
    percentEngine.inputDigit('0')
    percentEngine.percent()
    checkEquals("percent value", "0.5", percentEngine.current)

    val decimalEngine = CalculatorEngine()
    decimalEngine.inputDigit('0')
    decimalEngine.inputDigit('0')
    checkEquals("leading zeros collapse", "0", decimalEngine.current)
    decimalEngine.inputDecimal()
    decimalEngine.inputDigit('5')
    checkEquals("decimal input", "0.5", decimalEngine.current)
    decimalEngine.inputDecimal()
    checkEquals("single decimal point", "0.5", decimalEngine.current)
    decimalEngine.backspace()
    checkEquals("backspace", "0.", decimalEngine.current)
    decimalEngine.backspace()
    checkEquals("backspace to zero", "0", decimalEngine.current)

    val notesEngine = CalculatorEngine()
    notesEngine.inputDigit('1')
    notesEngine.inputDigit('0')
    notesEngine.setDraftNote(Side.LEFT, "مشتريات")
    notesEngine.chooseOperator("-")
    notesEngine.setDraftNote(Side.RIGHT, "خصم")
    notesEngine.inputDigit('4')
    val noted = notesEngine.equalsNow()
    checkEquals("left note saved", "مشتريات", noted?.leftNote)
    checkEquals("right note saved", "خصم", noted?.rightNote)
    notesEngine.updateNote(noted!!.id, Side.RIGHT, "  خصم الشهر  ")
    checkEquals("note trimmed", "خصم الشهر", notesEngine.findHistory(noted.id)?.rightNote)

    val resetDraft = engineWithNotes("سابق", "سابق")
    resetDraft.inputDigit('5')
    checkEquals("new calculation clears left note", "", resetDraft.draftNote(Side.LEFT))
    checkEquals("new calculation clears right note", "", resetDraft.draftNote(Side.RIGHT))
    checkEquals("new calculation keeps digit", "5", resetDraft.current)

    val ledgerEngine = CalculatorEngine()
    ledgerEngine.inputDigit('3')
    ledgerEngine.chooseOperator("+")
    ledgerEngine.inputDigit('2')
    ledgerEngine.chooseOperator("+")
    checkEquals("ledger points to latest", 1, ledgerEngine.ledgerOperationIds.size)
    ledgerEngine.inputDigit('9')
    ledgerEngine.equalsNow()
    checkEquals("ledger holds chain", 2, ledgerEngine.ledgerOperationIds.size)
    ledgerEngine.inputDigit('1')
    checkEquals("typing new number clears ledger", 0, ledgerEngine.ledgerOperationIds.size)
    checkEquals("history survives new typing", 2, ledgerEngine.history.size)
    checkEquals("last result kept", "14", ledgerEngine.lastResult)

    val limitEngine = CalculatorEngine(historyLimit = 2)
    limitEngine.inputDigit('1')
    repeat(3) {
        limitEngine.chooseOperator("+")
        limitEngine.inputDigit('1')
        limitEngine.equalsNow()
    }
    checkEquals("history limited", 2, limitEngine.history.size)

    val noteVisibility = CalculatorEngine()
    check("no note field on zero", !noteVisibility.canAddNote)
    noteVisibility.inputDigit('4')
    check("note field after digit", noteVisibility.canAddNote)
    noteVisibility.chooseOperator("+")
    check("note field after pendingOperator", noteVisibility.canAddNote)

    val restored = CalculatorEngine()
    restored.replaceHistory(
        listOf(
            Operation(1700000000000L, "5", "5", "+", "10", "أ", "ب", "12:00"),
            Operation(1699999999999L, "2", "3", "+", "5")
        )
    )
    checkEquals("restored history", 2, restored.history.size)
    checkEquals("restored ledger", 1, restored.ledgerOperationIds.size)
    checkEquals("restored ledger id", 1700000000000L, restored.ledgerOperation?.id)

    restored.clearHistory()
    checkEquals("clear history", 0, restored.history.size)
    checkEquals("clear ledger", 0, restored.ledgerOperationIds.size)

    var saves = 0
    val savingEngine = CalculatorEngine()
    savingEngine.onHistoryChanged = { saves++ }
    savingEngine.inputDigit('1')
    savingEngine.chooseOperator("+")
    savingEngine.inputDigit('2')
    savingEngine.equalsNow()
    checkEquals("persist hook called", 1, saves)
    savingEngine.updateNote(savingEngine.history[0].id, Side.LEFT, "ملاحظة")
    checkEquals("persist hook after note", 2, saves)

    println()
    println("total checks=$checks failures=$failures")
    if (failures > 0) {
        throw IllegalStateException("فشل $failures اختبار من $checks")
    }
}