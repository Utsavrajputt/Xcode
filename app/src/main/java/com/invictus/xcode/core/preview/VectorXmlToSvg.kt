package com.invictus.xcode.core.preview

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android VectorDrawable XML -> SVG, for the in-editor preview and the SVG / PNG export.
 *
 * Ported from the XML -> SVG half of the Modxtools converter (convertVectorToSvg /
 * renderVectorPath / androidGroupTransform), with the Android semantics it glossed over fixed:
 *  - `#AARRGGBB` becomes `#RRGGBB` + opacity (SVG's 8-digit form is RRGGBBAA, so passing it
 *    through would swap the channels).
 *  - A path without `fillColor` draws no fill, exactly like Android (the web tool made it black).
 *  - Strokes only render with a colour AND a width > 0, and keep cap / join / miter limit.
 *  - `<clip-path>` children and the root `android:alpha` are honoured.
 *  - Colours that can't be resolved here (`@color/x`, `?attr/x`, gradients) fall back to a
 *    neutral grey so the shape is still visible.
 */
object VectorXmlToSvg {

    sealed interface Result {
        data class Ok(val svg: String) : Result
        data class Err(val message: String) : Result
    }

    /** Past this the preview refuses, rather than building a DOM for something that isn't an icon. */
    const val MAX_CHARS = 2_000_000

    private const val FALLBACK_COLOR = "#8A8A8A"
    private const val DEFAULT_VIEWPORT = 24f

    private class Paint(val rgb: String, val alpha: Float)

    private class Ctx {
        val defs = StringBuilder()
        var clipCount = 0
    }

    fun convert(xml: String): Result {
        if (xml.length > MAX_CHARS) return Result.Err("File is too large to preview")
        val root = try {
            parse(xml)
        } catch (e: Exception) {
            return Result.Err(e.message?.lineSequence()?.firstOrNull()?.trim().orEmpty().ifEmpty { "Invalid XML" })
        }
        if (root.tagName != "vector") {
            return Result.Err("Root element is <${root.tagName}>, not <vector>")
        }

        val width = root.num("viewportWidth", 0f).takeIf { it > 0f } ?: dimension(root.attr("width")) ?: DEFAULT_VIEWPORT
        val height = root.num("viewportHeight", 0f).takeIf { it > 0f } ?: dimension(root.attr("height")) ?: DEFAULT_VIEWPORT

        val ctx = Ctx()
        val body = renderChildren(root, ctx)
        val alpha = root.num("alpha", 1f)

        val svg = StringBuilder()
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${f(width)}\" height=\"${f(height)}\"")
        svg.append(" viewBox=\"0 0 ${f(width)} ${f(height)}\">\n")
        if (ctx.defs.isNotEmpty()) svg.append("<defs>\n").append(ctx.defs).append("</defs>\n")
        if (alpha < 1f) svg.append("<g opacity=\"${f(alpha)}\">\n").append(body).append("</g>\n") else svg.append(body)
        svg.append("</svg>\n")
        return Result.Ok(svg.toString())
    }

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        // Not every parser implementation knows this feature; the preview works either way.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun renderChildren(parent: Element, ctx: Ctx): String {
        val out = StringBuilder()
        var node: Node? = parent.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                when (el.tagName) {
                    "path" -> out.append(renderPath(el))
                    "group" -> out.append(renderGroup(el, ctx))
                }
            }
            node = node.nextSibling
        }
        return out.toString()
    }

    /**
     * Android order: translate(translateX, translateY) -> pivot -> rotate -> scale -> -pivot,
     * which is the same left-to-right order an SVG transform list composes in.
     */
    private fun renderGroup(group: Element, ctx: Ctx): String {
        var body = renderChildren(group, ctx)

        // Every <clip-path> child clips the whole group (Android intersects them).
        var node: Node? = group.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE && (node as Element).tagName == "clip-path") {
                val d = node.attr("pathData")
                if (d != null) {
                    val id = "clip${++ctx.clipCount}"
                    ctx.defs.append("<clipPath id=\"$id\"><path d=\"${esc(d)}\"/></clipPath>\n")
                    body = "<g clip-path=\"url(#$id)\">\n$body</g>\n"
                }
            }
            node = node.nextSibling
        }

        val tx = group.num("translateX", 0f)
        val ty = group.num("translateY", 0f)
        val sx = group.num("scaleX", 1f)
        val sy = group.num("scaleY", 1f)
        val px = group.num("pivotX", 0f)
        val py = group.num("pivotY", 0f)
        val rotation = group.num("rotation", 0f)

        val transform = StringBuilder()
        if (tx != 0f || ty != 0f) transform.append("translate(${f(tx)} ${f(ty)}) ")
        if (rotation != 0f || sx != 1f || sy != 1f) {
            transform.append("translate(${f(px)} ${f(py)}) ")
            if (rotation != 0f) transform.append("rotate(${f(rotation)}) ")
            if (sx != 1f || sy != 1f) transform.append("scale(${f(sx)} ${f(sy)}) ")
            transform.append("translate(${f(-px)} ${f(-py)})")
        }
        return if (transform.isBlank()) body else "<g transform=\"${transform.toString().trim()}\">\n$body</g>\n"
    }

    private fun renderPath(path: Element): String {
        val d = path.attr("pathData") ?: return ""

        val fill = if (hasAaptAttr(path, "fillColor")) Paint(FALLBACK_COLOR, 1f) else parseColor(path.attr("fillColor"))
        val stroke = if (hasAaptAttr(path, "strokeColor")) Paint(FALLBACK_COLOR, 1f) else parseColor(path.attr("strokeColor"))
        val strokeWidth = path.num("strokeWidth", 0f)

        val sb = StringBuilder("<path d=\"${esc(d)}\"")
        if (fill == null) {
            sb.append(" fill=\"none\"")
        } else {
            sb.append(" fill=\"${fill.rgb}\"")
            val alpha = fill.alpha * path.num("fillAlpha", 1f)
            if (alpha < 1f) sb.append(" fill-opacity=\"${f(alpha)}\"")
        }
        if (stroke != null && strokeWidth > 0f) {
            sb.append(" stroke=\"${stroke.rgb}\" stroke-width=\"${f(strokeWidth)}\"")
            val alpha = stroke.alpha * path.num("strokeAlpha", 1f)
            if (alpha < 1f) sb.append(" stroke-opacity=\"${f(alpha)}\"")
            path.attr("strokeLineCap")?.let { sb.append(" stroke-linecap=\"${esc(it)}\"") }
            path.attr("strokeLineJoin")?.let { sb.append(" stroke-linejoin=\"${esc(it)}\"") }
            path.attr("strokeMiterLimit")?.toFloatOrNull()?.let { sb.append(" stroke-miterlimit=\"${f(it)}\"") }
        }
        if (path.attr("fillType") == "evenOdd") sb.append(" fill-rule=\"evenodd\"")
        sb.append("/>\n")
        return sb.toString()
    }

    /** `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB`; resource / theme references use the fallback grey. */
    private fun parseColor(raw: String?): Paint? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!value.startsWith("#")) return Paint(FALLBACK_COLOR, 1f)
        var hex = value.substring(1)
        if (hex.length == 3 || hex.length == 4) hex = hex.map { "$it$it" }.joinToString("")
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return Paint(FALLBACK_COLOR, 1f)
        return when (hex.length) {
            6 -> Paint("#$hex", 1f)
            8 -> Paint("#${hex.substring(2)}", hex.substring(0, 2).toInt(16) / 255f)
            else -> Paint(FALLBACK_COLOR, 1f)
        }
    }

    /** `<aapt:attr name="android:fillColor">` carries a gradient, which the preview can't draw. */
    private fun hasAaptAttr(el: Element, name: String): Boolean {
        var node: Node? = el.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val child = node as Element
                if (child.tagName == "aapt:attr" && child.getAttribute("name").endsWith(name)) return true
            }
            node = node.nextSibling
        }
        return false
    }

    /** `android:name` or the bare `name`, empty treated as absent. */
    private fun Node.attr(name: String): String? {
        val el = this as? Element ?: return null
        return el.getAttribute("android:$name").ifEmpty { el.getAttribute(name) }.ifEmpty { null }
    }

    private fun Element.num(name: String, default: Float): Float =
        dimension(attr(name)) ?: default

    /** "24dp" / "24" / "-1.5" -> number. */
    private fun dimension(raw: String?): Float? {
        val text = raw?.trim() ?: return null
        val end = text.indexOfFirst { !(it.isDigit() || it == '.' || it == '-') }
        return (if (end < 0) text else text.substring(0, end)).toFloatOrNull()
    }

    /** Whole numbers without ".0"; others to 4 decimals with trailing zeros dropped. */
    private fun f(v: Float): String {
        if (v == Math.rint(v.toDouble()).toFloat() && Math.abs(v) < 1e9f) return v.toLong().toString()
        return String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
