package ai.daylight.assistant.domain

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Tiny safe arithmetic evaluator for the Auto `calculate` tool. Numbers, + − × ÷ % ^,
 * parentheses, unary minus, and sqrt/abs/min/max/round. No identifiers, no assignments.
 */
object LocalCalculator {
    const val MAX_EXPRESSION_CHARS = 200

    fun evaluate(expression: String): Double {
        val clean = expression.trim()
        if (clean.isEmpty() || clean.length > MAX_EXPRESSION_CHARS) {
            throw IllegalArgumentException("Expression must be 1–$MAX_EXPRESSION_CHARS characters.")
        }
        val parser = Parser(tokenize(clean))
        val value = parser.parseExpression()
        parser.expectEnd()
        if (value.isNaN() || value.isInfinite()) {
            throw IllegalArgumentException("Result is not a finite number.")
        }
        return value
    }

    private sealed class Token {
        data class Number(val value: Double) : Token()
        data class Op(val symbol: Char) : Token()
        data class Ident(val name: String) : Token()
        data object LParen : Token()
        data object RParen : Token()
        data object Comma : Token()
    }

    private fun tokenize(source: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < source.length && source[i + 1].isDigit()) -> {
                    val start = i
                    i++
                    while (i < source.length && (source[i].isDigit() || source[i] == '.')) i++
                    val raw = source.substring(start, i)
                    tokens += Token.Number(raw.toDoubleOrNull() ?: throw IllegalArgumentException("Bad number: $raw"))
                }
                c.isLetter() -> {
                    val start = i
                    i++
                    while (i < source.length && source[i].isLetter()) i++
                    tokens += Token.Ident(source.substring(start, i).lowercase())
                }
                c == '(' -> { tokens += Token.LParen; i++ }
                c == ')' -> { tokens += Token.RParen; i++ }
                c == ',' -> { tokens += Token.Comma; i++ }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^' -> {
                    tokens += Token.Op(c); i++
                }
                else -> throw IllegalArgumentException("Unexpected character: $c")
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var index = 0

        fun parseExpression(): Double = parseAdd()

        private fun parseAdd(): Double {
            var value = parseMul()
            while (matchOp('+', '-')) {
                val op = previousOp()
                val rhs = parseMul()
                value = if (op == '+') value + rhs else value - rhs
            }
            return value
        }

        private fun parseMul(): Double {
            var value = parsePow()
            while (matchOp('*', '/', '%')) {
                val op = previousOp()
                val rhs = parsePow()
                value = when (op) {
                    '*' -> value * rhs
                    '/' -> {
                        if (rhs == 0.0) throw IllegalArgumentException("Division by zero.")
                        value / rhs
                    }
                    else -> {
                        if (rhs == 0.0) throw IllegalArgumentException("Modulo by zero.")
                        value % rhs
                    }
                }
            }
            return value
        }

        private fun parsePow(): Double {
            val base = parseUnary()
            if (!matchOp('^')) return base
            val exp = parsePow()
            return base.pow(exp)
        }

        private fun parseUnary(): Double {
            if (matchOp('+')) return parseUnary()
            if (matchOp('-')) return -parseUnary()
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            val token = peek() ?: throw IllegalArgumentException("Unexpected end of expression.")
            return when (token) {
                is Token.Number -> {
                    index++
                    token.value
                }
                is Token.Ident -> {
                    index++
                    when (token.name) {
                        "pi" -> Math.PI
                        "e" -> Math.E
                        "sqrt" -> {
                            expectLParen()
                            val arg = parseExpression()
                            expectRParen()
                            if (arg < 0.0) throw IllegalArgumentException("sqrt of a negative number.")
                            sqrt(arg)
                        }
                        "abs" -> {
                            expectLParen()
                            val arg = parseExpression()
                            expectRParen()
                            abs(arg)
                        }
                        "round" -> {
                            expectLParen()
                            val arg = parseExpression()
                            expectRParen()
                            round(arg)
                        }
                        "min" -> {
                            expectLParen()
                            val a = parseExpression()
                            expectComma()
                            val b = parseExpression()
                            expectRParen()
                            minOf(a, b)
                        }
                        "max" -> {
                            expectLParen()
                            val a = parseExpression()
                            expectComma()
                            val b = parseExpression()
                            expectRParen()
                            maxOf(a, b)
                        }
                        else -> throw IllegalArgumentException("Unknown name: ${token.name}")
                    }
                }
                is Token.LParen -> {
                    index++
                    val value = parseExpression()
                    expectRParen()
                    value
                }
                else -> throw IllegalArgumentException("Unexpected token.")
            }
        }

        fun expectEnd() {
            if (index < tokens.size) throw IllegalArgumentException("Unexpected trailing input.")
        }

        private fun peek(): Token? = tokens.getOrNull(index)

        private fun matchOp(vararg symbols: Char): Boolean {
            val token = peek() as? Token.Op ?: return false
            if (token.symbol !in symbols) return false
            index++
            return true
        }

        private fun previousOp(): Char = (tokens[index - 1] as Token.Op).symbol

        private fun expectLParen() {
            if (peek() !is Token.LParen) throw IllegalArgumentException("Expected '('.")
            index++
        }

        private fun expectRParen() {
            if (peek() !is Token.RParen) throw IllegalArgumentException("Expected ')'.")
            index++
        }

        private fun expectComma() {
            if (peek() !is Token.Comma) throw IllegalArgumentException("Expected ','.")
            index++
        }
    }
}
