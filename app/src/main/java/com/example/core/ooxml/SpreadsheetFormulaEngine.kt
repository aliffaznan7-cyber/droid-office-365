package com.example.core.ooxml

import com.example.domain.model.XlsxCell
import com.example.domain.model.XlsxWorksheet
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * On-device SpreadsheetML formula recalculation engine & number formatter (Part 1 Section 4.4).
 * Resolves <f> formula expressions into updated <v> cached values with cycle detection.
 */
object SpreadsheetFormulaEngine {

    fun recalculateWorksheet(worksheet: XlsxWorksheet): XlsxWorksheet {
        val cells = worksheet.cells.toMutableMap()
        val cache = HashMap<Pair<Int, Int>, Double?>()
        val visiting = HashSet<Pair<Int, Int>>()

        fun evalCell(coord: Pair<Int, Int>): Double? {
            if (cache.containsKey(coord)) return cache[coord]
            if (!visiting.add(coord)) return null // Cycle protection
            val cell = cells[coord]
            val result = if (cell == null) {
                0.0
            } else if (!cell.formula.isNullOrBlank()) {
                evaluateExpression(cell.formula, ::evalCell)
            } else {
                cell.cachedValue.replace(",", "").toDoubleOrNull()
            }
            visiting.remove(coord)
            cache[coord] = result
            return result
        }

        cells.keys.toList().forEach { coord ->
            val cell = cells[coord] ?: return@forEach
            if (!cell.formula.isNullOrBlank()) {
                val numeric = evalCell(coord)
                val rawResult = if (numeric != null) formatNumericRaw(numeric) else "#ERR!"
                cells[coord] = cell.copy(
                    cachedValue = rawResult,
                    displayValue = formatDisplayValue(rawResult, cell.numberFormat)
                )
            } else {
                cells[coord] = cell.copy(
                    displayValue = formatDisplayValue(cell.cachedValue, cell.numberFormat)
                )
            }
        }
        return worksheet.copy(cells = cells)
    }

    fun formatDisplayValue(rawValue: String, numberFormat: String): String {
        val num = rawValue.toDoubleOrNull() ?: return rawValue
        return when {
            numberFormat.contains("RM", ignoreCase = true) -> {
                "RM ${DecimalFormat("#,##0.00").format(num)}"
            }
            numberFormat.contains("$") -> {
                "$${DecimalFormat("#,##0.00").format(num)}"
            }
            numberFormat.contains("%") -> {
                "${DecimalFormat("0.0").format(num * 100.0)}%"
            }
            numberFormat.contains("#,##0.00") -> {
                DecimalFormat("#,##0.00").format(num)
            }
            numberFormat.contains("#,##0") -> {
                DecimalFormat("#,##0").format(num)
            }
            else -> formatNumericRaw(num)
        }
    }

    private fun formatNumericRaw(value: Double): String {
        return if (value.isNaN() || value.isInfinite()) "#VALUE!"
        else if (value == value.roundToLong().toDouble()) value.roundToLong().toString()
        else String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }

    fun colIndexToName(colIndex: Int): String {
        var n = colIndex + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun parseCellRef(ref: String): Pair<Int, Int>? {
        val cleaned = ref.trim().replace("$", "").uppercase(Locale.US)
        val match = Regex("^([A-Z]+)([0-9]+)$").matchEntire(cleaned) ?: return null
        val colStr = match.groupValues[1]
        val rowNum = match.groupValues[2].toIntOrNull() ?: return null
        var col = 0
        for (ch in colStr) {
            col = col * 26 + (ch.code - 'A'.code + 1)
        }
        return (rowNum - 1) to (col - 1)
    }

    private fun evaluateExpression(
        rawFormula: String,
        resolveCell: (Pair<Int, Int>) -> Double?
    ): Double? {
        val expr = rawFormula.trim().removePrefix("=").trim()
        if (expr.isEmpty()) return null
        return Parser(expr, resolveCell).parseComparison()
    }

    private class Parser(
        private val input: String,
        private val resolveCell: (Pair<Int, Int>) -> Double?
    ) {
        private var pos = 0

        fun parseComparison(): Double? {
            val left = parseAddSub() ?: return null
            skipWs()
            if (pos >= input.length) return left
            val op = when {
                input.startsWith(">=", pos) -> ">="
                input.startsWith("<=", pos) -> "<="
                input.startsWith("<>", pos) -> "<>"
                input[pos] == '>' -> ">"
                input[pos] == '<' -> "<"
                input[pos] == '=' -> "="
                else -> return left
            }
            pos += op.length
            val right = parseAddSub() ?: return null
            val truth = when (op) {
                ">=" -> left >= right
                "<=" -> left <= right
                "<>" -> left != right
                ">" -> left > right
                "<" -> left < right
                "=" -> left == right
                else -> false
            }
            return if (truth) 1.0 else 0.0
        }

        private fun parseAddSub(): Double? {
            var left = parseMulDiv() ?: return null
            while (true) {
                skipWs()
                if (pos >= input.length) break
                when (input[pos]) {
                    '+' -> {
                        pos++
                        left += parseMulDiv() ?: return null
                    }
                    '-' -> {
                        pos++
                        left -= parseMulDiv() ?: return null
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseMulDiv(): Double? {
            var left = parseUnary() ?: return null
            while (true) {
                skipWs()
                if (pos >= input.length) break
                when (input[pos]) {
                    '*' -> {
                        pos++
                        left *= parseUnary() ?: return null
                    }
                    '/' -> {
                        pos++
                        val rhs = parseUnary() ?: return null
                        if (rhs == 0.0) return Double.NaN
                        left /= rhs
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseUnary(): Double? {
            skipWs()
            if (pos < input.length && input[pos] == '-') {
                pos++
                return parseUnary()?.let { -it }
            }
            if (pos < input.length && input[pos] == '+') {
                pos++
                return parseUnary()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double? {
            skipWs()
            if (pos >= input.length) return null
            if (input[pos] == '(') {
                pos++
                val v = parseComparison()
                skipWs()
                if (pos < input.length && input[pos] == ')') pos++
                return v
            }

            if (input[pos].isDigit() || input[pos] == '.') {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                return input.substring(start, pos).toDoubleOrNull()
            }

            // Identifier: function or cell reference
            val start = pos
            while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '$' || input[pos] == '_')) {
                pos++
            }
            val token = input.substring(start, pos).uppercase(Locale.US)
            skipWs()
            if (pos < input.length && input[pos] == '(') {
                pos++
                return parseFunction(token)
            }
            val coord = parseCellRef(token) ?: return null
            return resolveCell(coord) ?: 0.0
        }

        private fun parseFunction(name: String): Double? {
            if (name == "IF") {
                val cond = parseComparison() ?: 0.0
                skipComma()
                val trueVal = parseComparison() ?: 0.0
                skipComma()
                val falseVal = parseComparison() ?: 0.0
                skipClosingParen()
                return if (cond != 0.0) trueVal else falseVal
            }

            val values = mutableListOf<Double>()
            while (pos < input.length) {
                skipWs()
                if (pos < input.length && input[pos] == ')') {
                    pos++
                    break
                }
                val savedPos = pos
                // Check for range A1:B5
                val rangeMatch = Regex("^([A-Z$]+[0-9]+)\\s*:\\s*([A-Z$]+[0-9]+)", RegexOption.IGNORE_CASE)
                    .find(input.substring(pos))
                if (rangeMatch != null) {
                    val startRef = parseCellRef(rangeMatch.groupValues[1])
                    val endRef = parseCellRef(rangeMatch.groupValues[2])
                    if (startRef != null && endRef != null) {
                        pos += rangeMatch.range.last + 1
                        val rMin = minOf(startRef.first, endRef.first)
                        val rMax = maxOf(startRef.first, endRef.first)
                        val cMin = minOf(startRef.second, endRef.second)
                        val cMax = maxOf(startRef.second, endRef.second)
                        for (r in rMin..rMax) {
                            for (c in cMin..cMax) {
                                resolveCell(r to c)?.let { values.add(it) }
                            }
                        }
                    } else {
                        pos = savedPos
                        parseComparison()?.let { values.add(it) }
                    }
                } else {
                    parseComparison()?.let { values.add(it) }
                }
                skipWs()
                if (pos < input.length && input[pos] == ',') pos++
            }

            return when (name) {
                "SUM" -> values.sum()
                "AVERAGE", "AVG" -> if (values.isEmpty()) 0.0 else values.average()
                "MIN" -> values.minOrNull() ?: 0.0
                "MAX" -> values.maxOrNull() ?: 0.0
                "COUNT" -> values.size.toDouble()
                "ROUND" -> {
                    val v = values.getOrNull(0) ?: 0.0
                    val d = (values.getOrNull(1) ?: 0.0).toInt().coerceIn(0, 6)
                    val factor = Math.pow(10.0, d.toDouble())
                    kotlin.math.round(v * factor) / factor
                }
                else -> values.firstOrNull()
            }
        }

        private fun skipComma() {
            skipWs()
            if (pos < input.length && input[pos] == ',') pos++
        }

        private fun skipClosingParen() {
            skipWs()
            if (pos < input.length && input[pos] == ')') pos++
        }

        private fun skipWs() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }
    }
}
