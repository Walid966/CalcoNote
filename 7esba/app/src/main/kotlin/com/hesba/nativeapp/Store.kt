package com.hesba.nativeapp

import android.content.Context
import android.content.SharedPreferences
import com.hesba.core.CalculatorEngine
import com.hesba.core.Operation
import com.hesba.core.SessionState
import org.json.JSONArray
import org.json.JSONObject

/** تخزين محلي بسيط لكل بيانات التطبيق (سجل العمليات + الحسبة الجارية). */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadHistory(): List<Operation> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> toOperation(array.getJSONObject(index)) }
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun saveHistory(items: List<Operation>) {
        val array = JSONArray()
        items.forEach { operation ->
            array.put(
                JSONObject()
                    .put("id", operation.id)
                    .put("left", operation.left)
                    .put("right", operation.right)
                    .put("pendingOperator", operation.pendingOperator)
                    .put("result", operation.result)
                    .put("leftNote", operation.leftNote)
                    .put("rightNote", operation.rightNote)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun saveSession(engine: CalculatorEngine) {
        val state = engine.snapshot()
        val json = JSONObject()
            .put("current", state.current)
            .put("firstOperand", state.firstOperand ?: JSONObject.NULL)
            .put("pendingOperator", state.pendingOperator ?: JSONObject.NULL)
            .put("waitingForSecond", state.waitingForSecond)
            .put("expression", state.expression)
            .put("leftNote", state.leftNote)
            .put("rightNote", state.rightNote)
            .put("lastResult", state.lastResult ?: JSONObject.NULL)
        prefs.edit().putString(KEY_SESSION, json.toString()).apply()
    }

    fun loadSession(): SessionState? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val json = JSONObject(raw)
            SessionState(
                current = json.optString("current", "0"),
                firstOperand = if (json.isNull("firstOperand")) null else json.optDouble("firstOperand"),
                pendingOperator = if (json.isNull("pendingOperator")) null else json.optString("pendingOperator"),
                waitingForSecond = json.optBoolean("waitingForSecond", false),
                expression = json.optString("expression", ""),
                leftNote = json.optString("leftNote", ""),
                rightNote = json.optString("rightNote", ""),
                lastResult = if (json.isNull("lastResult")) null else json.optString("lastResult")
            )
        } catch (error: Exception) {
            null
        }
    }

    private fun toOperation(json: JSONObject) = Operation(
        id = json.optLong("id"),
        left = json.optString("left"),
        right = json.optString("right"),
        pendingOperator = json.optString("pendingOperator"),
        result = json.optString("result"),
        leftNote = json.optString("leftNote"),
        rightNote = json.optString("rightNote")
    )

    private companion object {
        const val PREFS_NAME = "hesba-store"
        const val KEY_HISTORY = "history-v1"
        const val KEY_SESSION = "session-v1"
    }
}