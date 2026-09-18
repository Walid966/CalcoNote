package com.hesba.core

import java.math.BigDecimal
import java.math.RoundingMode

/** الجهة التي تنتمي إليها الملاحظة داخل العملية. */
enum class Side(val key: String) {
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun fromKey(key: String?): Side = if (key == LEFT.key) LEFT else RIGHT
    }
}

/** عملية حسابية واحدة مع ملاحظات الرقمين. */
data class Operation(
    val id: Long,
    val left: String,
    val right: String,
    val pendingOperator: String,
    val result: String,
    val leftNote: String = "",
    val rightNote: String = "",
    val createdAt: String = ""
) {
    fun note(side: Side): String = if (side == Side.LEFT) leftNote else rightNote

    fun withNote(side: Side, value: String): Operation =
        if (side == Side.LEFT) copy(leftNote = value) else copy(rightNote = value)
}

/** رمز العملية كما يظهر للمستخدم. */
fun operatorSymbol(pendingOperator: String): String = when (pendingOperator) {
    "+" -> "+"
    "-" -> "−"
    "*" -> "×"
    "/" -> "÷"
    else -> pendingOperator
}

/** حالة الحسبة الجارية حتى تُستعاد كما هي بعد إغلاق التطبيق. */
data class SessionState(
    val current: String = "0",
    val firstOperand: Double? = null,
    val pendingOperator: String? = null,
    val waitingForSecond: Boolean = false,
    val expression: String = "",
    val leftNote: String = "",
    val rightNote: String = "",
    val lastResult: String? = null
)

/** تنسيق الرقم بنفس أسلوب النسخة السابقة: بدون كسور زائدة وبحد أقصى 8 منازل. */
fun formatNumber(value: Double): String {
    if (!value.isFinite()) return "خطأ"
    if (value == Math.floor(value) && Math.abs(value) < 1e15) return value.toLong().toString()
    return BigDecimal(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
