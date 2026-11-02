package com.valghora.shrimpl.core.icons

import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** The SVG or image can't be used as an icon; the message says why. */
class IconException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** An SVG viewBox. */
data class ViewBox(val minX: Double, val minY: Double, val width: Double, val height: Double)

/**
 * A single-color glyph from an SVG (docs/SHRIMPL_SPEC.md R10):
 * its viewBox, its path data normalized for Android's PathParser, and its fill rule.
 */
data class SvgGlyph(val viewBox: ViewBox, val pathData: String, val evenOdd: Boolean) {
    companion object {
        private const val GLYPH_ONLY = "The icon must be a single-color glyph made only of <path> elements"
        private val IGNORED_ELEMENTS = setOf("title", "desc", "metadata")

        /** Parses and checks a glyph SVG; throws [IconException] for anything that isn't one. */
        fun parse(data: ByteArray): SvgGlyph {
            val text = data.decodeToString()
            // No DTDs or entities: they are never needed for a glyph and are an XML attack surface.
            if ("<!DOCTYPE" in text || "<!ENTITY" in text) throw IconException("SVG with a DOCTYPE is not supported")
            val root = try {
                DocumentBuilderFactory.newInstance()
                    .apply {
                        isNamespaceAware = true
                        isExpandEntityReferences = false
                    }
                    .newDocumentBuilder()
                    .parse(ByteArrayInputStream(data))
                    .documentElement
            } catch (e: Exception) {
                throw IconException("The icon is not valid XML: ${e.message}", e)
            }
            if (localName(root) != "svg") throw IconException("The icon's root element is not <svg>")

            val viewBoxText = root.attr("viewBox") ?: throw IconException("The SVG has no viewBox")
            val numbers = viewBoxText.trim().split(Regex("[\\s,]+")).map { it.toDoubleOrNull() }
            if (numbers.size != 4 || numbers.any { it == null }) throw IconException("Invalid viewBox '$viewBoxText'")
            val (minX, minY, width, height) = numbers.map { it!! }
            if (width <= 0 || height <= 0) throw IconException("Invalid viewBox '$viewBoxText'")

            val paths = mutableListOf<Pair<String, String>>()
            fun walk(element: Element, inheritedFill: String, inheritedRule: String) {
                val name = localName(element)
                if (name in IGNORED_ELEMENTS) return
                if (name !in setOf("svg", "g", "path")) throw IconException("$GLYPH_ONLY (found <$name>)")
                val style = styleProps(element)
                if (element.attr("transform") != null || "transform" in style) {
                    throw IconException("$GLYPH_ONLY (transform on <$name>)")
                }
                val fill = presentation(element, style, "fill", inheritedFill)
                val rule = presentation(element, style, "fill-rule", inheritedRule)
                if (name == "path") {
                    val d = element.attr("d")?.trim().orEmpty()
                    if (fill == "none") throw IconException("$GLYPH_ONLY (stroke-only or unfilled <path>)")
                    if (fill.startsWith("url(")) throw IconException("$GLYPH_ONLY (gradient or pattern fill)")
                    if (d.isNotEmpty()) paths += d to rule
                }
                val children = element.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child.nodeType == Node.ELEMENT_NODE) walk(child as Element, fill, rule)
                }
            }
            walk(root, "black", "nonzero")
            if (paths.isEmpty()) throw IconException("The SVG contains no <path d> data")
            return SvgGlyph(
                viewBox = ViewBox(minX, minY, width, height),
                pathData = PathData.androidSafe(paths.map { it.first }),
                evenOdd = paths.any { it.second == "evenodd" },
            )
        }

        private fun localName(element: Element) = element.localName ?: element.tagName.substringAfter(':')

        private fun Element.attr(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null

        private fun styleProps(element: Element): Map<String, String> =
            element.attr("style").orEmpty().split(';').filter { ':' in it }.associate {
                it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()
            }

        private fun presentation(element: Element, style: Map<String, String>, name: String, inherited: String): String {
            val value = style[name] ?: element.attr(name)
            return if (value == null || value == "" || value == "inherit") inherited else value.trim()
        }
    }
}

/**
 * SVG path data rewritten so Android's PathParser reads it correctly.
 *
 * Invariant: every number is written out separately. Minified path data trips PathParser: arc flags
 * written without separators ("00-4.88") and chained decimals ("-1.51.074") make it miscount
 * parameters and silently draw nothing (`SvgGlyphTest.simpleIconsGlyphNormalizes`).
 */
object PathData {
    private val ARITY = mapOf('M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2, 'A' to 7, 'Z' to 0)
    private const val COMMANDS = "MmLlHhVvCcSsQqTtAaZz"
    private val NUMBER = Regex("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?")

    /** Path data for several `<path d>` strings, joined into one normalized string. */
    fun androidSafe(paths: List<String>): String = paths.joinToString(" ") { serialize(groups(tokenize(it))) }

    private sealed interface Token {
        data class Command(val letter: Char) : Token
        data class Number(val value: Double) : Token
    }

    private fun tokenize(d: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var command: Char? = null
        var index = 0
        while (i < d.length) {
            val ch = d[i]
            if (ch.isWhitespace() || ch == ',') {
                i++
                continue
            }
            if (ch in COMMANDS) {
                tokens += Token.Command(ch)
                command = ch
                index = 0
                i++
                continue
            }
            if (command == null) throw IconException("Path data must start with a command (offset $i)")
            if ((command == 'A' || command == 'a') && index % 7 in setOf(3, 4)) {
                if (ch != '0' && ch != '1') throw IconException("Invalid arc flag '$ch' at offset $i")
                tokens += Token.Number(if (ch == '1') 1.0 else 0.0)
                index++
                i++
                continue
            }
            val match = NUMBER.matchAt(d, i) ?: throw IconException("Unexpected '$ch' in path data at offset $i")
            tokens += Token.Number(match.value.toDouble())
            index++
            i = match.range.last + 1
        }
        return tokens
    }

    private fun groups(tokens: List<Token>): List<Pair<Char, List<Double>>> {
        val groups = mutableListOf<Pair<Char, List<Double>>>()
        var i = 0
        while (i < tokens.size) {
            val command = (tokens[i] as? Token.Command)?.letter ?: throw IconException("Expected a command")
            i++
            val params = mutableListOf<Double>()
            while (i < tokens.size && tokens[i] is Token.Number) {
                params += (tokens[i] as Token.Number).value
                i++
            }
            val arity = ARITY.getValue(command.uppercaseChar())
            if (arity == 0) {
                if (params.isNotEmpty()) throw IconException("Command $command takes no parameters")
                groups += command to emptyList()
                continue
            }
            if (params.isEmpty() || params.size % arity != 0) {
                throw IconException("Command $command has ${params.size} parameters; expected a multiple of $arity")
            }
            for (k in params.indices step arity) {
                // Extra coordinate pairs after a moveto are implicit linetos.
                val letter = if (k > 0 && command == 'M') 'L' else if (k > 0 && command == 'm') 'l' else command
                groups += letter to params.subList(k, k + arity)
            }
        }
        // The first moveto of a path is absolute even when written lowercase; keep that meaning when
        // several <path> elements are concatenated.
        if (groups.isNotEmpty() && groups[0].first == 'm') groups[0] = 'M' to groups[0].second
        return groups
    }

    private fun serialize(groups: List<Pair<Char, List<Double>>>): String = buildList {
        for ((command, params) in groups) {
            add(command.toString())
            params.forEachIndexed { index, value ->
                add(if ((command == 'A' || command == 'a') && index in setOf(3, 4)) (if (value != 0.0) "1" else "0") else format(value))
            }
        }
    }.joinToString(" ")

    /** At most 4 decimals, no trailing zeros, no exponent, leading zero kept, never "-0". */
    fun format(value: Double): String {
        val text = String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')
        return if (text == "" || text == "-" || text == "-0") "0" else text
    }
}
