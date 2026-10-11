package com.invictus.kodex.github.data

/** One `workflow_dispatch` input (plan section 4). [type] is `string` when the YAML omits it. */
data class WorkflowInput(
    val name: String,
    val description: String,
    val required: Boolean,
    val type: String,
    val default: String?,
    val options: List<String>,
)

/**
 * What a workflow file says about manual runs.
 * [parseFailed] = the extractor could not be sure; the UI then offers a raw key/value editor
 * so the user can still dispatch (plan 8.2).
 */
data class DispatchInfo(
    val supported: Boolean,
    val inputs: List<WorkflowInput>,
    val parseFailed: Boolean = false,
)

/**
 * Minimal indentation-based extractor for `on.workflow_dispatch.inputs` (plan 8.2: no YAML
 * dependency). Pure and Android-free. Handles `on: workflow_dispatch`, `on: [a, b]`, the list
 * form, `workflow_dispatch: {}` / empty, quoted keys, comments, folded descriptions and block or
 * flow `options`.
 */
object WorkflowInputParser {

    private class Line(val indent: Int, val text: String)

    fun parse(yaml: String): DispatchInfo {
        val lines = yaml.lineSequence()
            .map { it.trimEnd('\r') }
            .map { stripComment(it) }
            .filter { it.isNotBlank() }
            .map { Line(it.length - it.trimStart().length, it.trim()) }
            .toList()

        val onIdx = lines.indexOfFirst { it.indent == 0 && ON_KEY.matches(it.text) }
        if (onIdx < 0) return DispatchInfo(supported = true, inputs = emptyList(), parseFailed = true)

        val rest = ON_KEY.matchEntire(lines[onIdx].text)!!.groupValues[1].trim()
        if (rest.isNotEmpty()) return parseInlineOn(rest)

        // Block form: every following line with indent > 0 belongs to `on`.
        var end = onIdx + 1
        while (end < lines.size && lines[end].indent > 0) end++
        val block = lines.subList(onIdx + 1, end)
        if (block.isEmpty()) return DispatchInfo(supported = false, inputs = emptyList())

        val base = block.first().indent
        // List form: "- workflow_dispatch"
        if (block.first().text.startsWith("-")) {
            val has = block.any { it.indent == base && unquote(it.text.removePrefix("-").trim()) == DISPATCH }
            return DispatchInfo(supported = has, inputs = emptyList())
        }

        val wdIdx = block.indexOfFirst { it.indent == base && keyOf(it.text) == DISPATCH }
        if (wdIdx < 0) return DispatchInfo(supported = false, inputs = emptyList())

        val wdRest = valueOf(block[wdIdx].text)
        var wdEnd = wdIdx + 1
        while (wdEnd < block.size && block[wdEnd].indent > base) wdEnd++
        val wdBody = block.subList(wdIdx + 1, wdEnd)
        if (wdBody.isEmpty()) {
            // `workflow_dispatch:` / `{}` / `~` / `null`; anything else inline is unexpected.
            val trivial = wdRest.isEmpty() || wdRest == "{}" || wdRest == "~" || wdRest == "null"
            return DispatchInfo(supported = true, inputs = emptyList(), parseFailed = !trivial)
        }

        val childIndent = wdBody.first().indent
        val inputsIdx = wdBody.indexOfFirst { it.indent == childIndent && keyOf(it.text) == "inputs" }
        if (inputsIdx < 0) return DispatchInfo(supported = true, inputs = emptyList())
        val inputsRest = valueOf(wdBody[inputsIdx].text)
        if (inputsRest.isNotEmpty() && inputsRest != "{}") {
            return DispatchInfo(supported = true, inputs = emptyList(), parseFailed = true)
        }
        var inEnd = inputsIdx + 1
        while (inEnd < wdBody.size && wdBody[inEnd].indent > childIndent) inEnd++
        val inputLines = wdBody.subList(inputsIdx + 1, inEnd)
        if (inputLines.isEmpty()) return DispatchInfo(supported = true, inputs = emptyList())

        val inputs = parseInputs(inputLines)
        return DispatchInfo(supported = true, inputs = inputs, parseFailed = inputs.isEmpty())
    }

    private fun parseInlineOn(rest: String): DispatchInfo {
        val v = unquote(rest)
        return when {
            v == DISPATCH -> DispatchInfo(supported = true, inputs = emptyList())
            rest.startsWith("[") -> {
                val items = flowList(rest)
                DispatchInfo(supported = DISPATCH in items, inputs = emptyList())
            }
            rest.startsWith("{") -> {
                val has = Regex("""(^|[{,\s])["']?$DISPATCH["']?\s*:""").containsMatchIn(rest)
                DispatchInfo(supported = has, inputs = emptyList(), parseFailed = has && rest.contains("inputs"))
            }
            else -> DispatchInfo(supported = false, inputs = emptyList())
        }
    }

    private fun parseInputs(lines: List<Line>): List<WorkflowInput> {
        val nameIndent = lines.first().indent
        val out = mutableListOf<WorkflowInput>()
        var i = 0
        while (i < lines.size) {
            val head = lines[i]
            if (head.indent != nameIndent || !head.text.contains(":")) { i++; continue }
            val name = unquote(keyOf(head.text))
            var j = i + 1
            while (j < lines.size && lines[j].indent > nameIndent) j++
            out += parseOneInput(name, lines.subList(i + 1, j))
            i = j
        }
        return out
    }

    private fun parseOneInput(name: String, body: List<Line>): WorkflowInput {
        var description = ""
        var required = false
        var type = "string"
        var default: String? = null
        var options: List<String> = emptyList()
        if (body.isNotEmpty()) {
            val c = body.first().indent
            var k = 0
            while (k < body.size) {
                val ln = body[k]
                if (ln.indent != c || ln.text.startsWith("-")) { k++; continue }
                val key = keyOf(ln.text)
                val raw = valueOf(ln.text)
                k++
                when (key) {
                    "options" -> {
                        if (raw.startsWith("[")) {
                            options = flowList(raw)
                        } else if (raw.isEmpty()) {
                            // Block list; items may sit at the key's own indent.
                            val items = mutableListOf<String>()
                            while (k < body.size && body[k].text.startsWith("-") && body[k].indent >= c) {
                                items += unquote(body[k].text.removePrefix("-").trim())
                                k++
                            }
                            options = items
                        }
                    }
                    "description", "default", "type", "required" -> {
                        val value = if (raw in FOLD_MARKERS) {
                            val parts = mutableListOf<String>()
                            while (k < body.size && body[k].indent > c) { parts += body[k].text; k++ }
                            parts.joinToString(" ")
                        } else unquote(raw)
                        when (key) {
                            "description" -> description = value
                            "default" -> default = value
                            "type" -> type = value.ifEmpty { "string" }
                            "required" -> required = value.equals("true", ignoreCase = true)
                        }
                    }
                }
            }
        }
        return WorkflowInput(name, description, required, type, default, options)
    }

    // ---- helpers ------------------------------------------------------------------------

    private const val DISPATCH = "workflow_dispatch"
    private val FOLD_MARKERS = setOf(">", "|", ">-", "|-", ">+", "|+")
    private val ON_KEY = Regex("""^(?:"on"|'on'|on|true)\s*:(.*)$""")

    private fun keyOf(text: String): String {
        val t = text.removePrefix("- ").trim()
        // A quoted key may contain ':'; otherwise the key ends at the first ':'.
        if (t.startsWith("\"") || t.startsWith("'")) {
            val q = t[0]
            val close = t.indexOf(q, 1)
            if (close > 0) return t.substring(1, close)
        }
        val idx = t.indexOf(':')
        return if (idx < 0) t else t.substring(0, idx).trim()
    }

    private fun valueOf(text: String): String {
        val t = text.trim()
        val start = if (t.startsWith("\"") || t.startsWith("'")) {
            val close = t.indexOf(t[0], 1)
            if (close > 0) t.indexOf(':', close) else t.indexOf(':')
        } else t.indexOf(':')
        return if (start < 0) "" else t.substring(start + 1).trim()
    }

    private fun flowList(s: String): List<String> {
        val inner = s.trim().removePrefix("[").removeSuffix("]")
        if (inner.isBlank()) return emptyList()
        return splitTopLevel(inner).map { unquote(it.trim()) }.filter { it.isNotEmpty() }
    }

    /** Splits on commas that are not inside quotes. */
    private fun splitTopLevel(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (ch in s) {
            when {
                quote != null -> { cur.append(ch); if (ch == quote) quote = null }
                ch == '"' || ch == '\'' -> { quote = ch; cur.append(ch) }
                ch == ',' -> { out += cur.toString(); cur.setLength(0) }
                else -> cur.append(ch)
            }
        }
        out += cur.toString()
        return out
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        if (t.length >= 2 && ((t.first() == '"' && t.last() == '"') || (t.first() == '\'' && t.last() == '\''))) {
            return t.substring(1, t.length - 1)
        }
        return t
    }

    /** Drops a trailing ` # comment` (and whole-line comments) while respecting quotes. */
    private fun stripComment(line: String): String {
        var quote: Char? = null
        for (i in line.indices) {
            val ch = line[i]
            when {
                quote != null -> if (ch == quote) quote = null
                ch == '"' || ch == '\'' -> quote = ch
                ch == '#' && (i == 0 || line[i - 1].isWhitespace()) -> return line.substring(0, i).trimEnd()
            }
        }
        return line
    }
}
