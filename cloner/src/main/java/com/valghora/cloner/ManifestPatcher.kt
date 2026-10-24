package com.valghora.cloner

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads and rewrites a compiled (binary XML) AndroidManifest.xml, the form it takes inside an APK.
 *
 * A copy of an app installs as a separate app when:
 * - every string equal to the old package name, or starting with it followed by ".", uses the new
 *   package name instead. Besides the package itself, this renames permissions and provider
 *   authorities derived from it; leaving them would make the copy fail to install next to the
 *   original (duplicate permission, conflicting provider).
 * - optionally, the application label becomes a literal string, so the copy shows its own name.
 *
 * Only the string pool is rebuilt; the element tree keeps its layout. Plain JVM code, so it runs in
 * host unit tests as well as on the device.
 */
object ManifestPatcher {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val STRING_POOL_HEADER_SIZE = 28
    private const val SORTED_FLAG = 0x1
    private const val UTF8_FLAG = 0x100
    private const val TYPE_STRING = 0x03
    private const val ATTR_LABEL = 0x01010001

    data class ManifestInfo(
        val packageName: String,
        /** The literal label, or null if the label is a resource reference (or missing). */
        val literalLabel: String?,
        val strings: List<String>,
    )

    class ManifestFormatException(message: String) : RuntimeException(message)

    fun read(manifest: ByteArray): ManifestInfo {
        val doc = Document.parse(manifest)
        val label = doc.applicationLabel()
        val literal = label?.takeIf { it.dataType == TYPE_STRING }?.let { doc.pool.strings[it.data] }
        return ManifestInfo(doc.packageName(), literal, doc.pool.strings.toList())
    }

    fun patch(manifest: ByteArray, newPackage: String, newLabel: String? = null): ByteArray {
        val doc = Document.parse(manifest)
        val oldPackage = doc.packageName()
        val strings = doc.pool.strings
        for (i in strings.indices) {
            val s = strings[i]
            if (s == oldPackage || s.startsWith("$oldPackage.")) strings[i] = newPackage + s.substring(oldPackage.length)
        }
        if (newLabel != null) {
            val label = doc.applicationLabel() ?: throw ManifestFormatException("<application> has no android:label")
            strings.add(newLabel)
            label.setString(strings.lastIndex)
        }
        return doc.encode()
    }

    // ---------- Binary XML model ----------

    private class StringPool(
        val strings: MutableList<String>,
        val flags: Int,
        val styleOffsets: IntArray,
        val styleData: ByteArray,
    ) {
        val utf8 get() = flags and UTF8_FLAG != 0

        fun encode(): ByteArray {
            val data = ByteArrayOutputStream()
            val offsets = IntArray(strings.size)
            strings.forEachIndexed { i, s ->
                offsets[i] = data.size()
                if (utf8) {
                    val bytes = s.toByteArray(Charsets.UTF_8)
                    writeUtf8Length(data, s.length)
                    writeUtf8Length(data, bytes.size)
                    data.write(bytes)
                    data.write(0)
                } else {
                    writeUtf16Length(data, s.length)
                    for (c in s) {
                        data.write(c.code and 0xFF)
                        data.write(c.code ushr 8)
                    }
                    data.write(0)
                    data.write(0)
                }
            }
            while (data.size() % 4 != 0) data.write(0)

            val stringsStart = STRING_POOL_HEADER_SIZE + 4 * (strings.size + styleOffsets.size)
            val stylesStart = if (styleOffsets.isEmpty()) 0 else stringsStart + data.size()
            val size = stringsStart + data.size() + styleData.size
            val out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            out.putShort(RES_STRING_POOL_TYPE.toShort())
            out.putShort(STRING_POOL_HEADER_SIZE.toShort())
            out.putInt(size)
            out.putInt(strings.size)
            out.putInt(styleOffsets.size)
            out.putInt(flags and SORTED_FLAG.inv())
            out.putInt(stringsStart)
            out.putInt(stylesStart)
            offsets.forEach(out::putInt)
            styleOffsets.forEach(out::putInt)
            out.put(data.toByteArray())
            out.put(styleData)
            return out.array()
        }

        companion object {
            fun parse(buf: ByteBuffer, start: Int): StringPool {
                val size = buf.getInt(start + 4)
                val count = buf.getInt(start + 8)
                val styleCount = buf.getInt(start + 12)
                val flags = buf.getInt(start + 16)
                val stringsStart = start + buf.getInt(start + 20)
                val stylesStart = buf.getInt(start + 24)
                val offsetsAt = start + (buf.getShort(start + 2).toInt() and 0xFFFF)
                val strings = MutableList(count) { i ->
                    val at = stringsStart + buf.getInt(offsetsAt + 4 * i)
                    if (flags and UTF8_FLAG != 0) readUtf8(buf, at) else readUtf16(buf, at)
                }
                val styleOffsets = IntArray(styleCount) { buf.getInt(offsetsAt + 4 * (count + it)) }
                val styleData = if (styleCount == 0) ByteArray(0) else {
                    ByteArray(size - stylesStart).also { buf.get(start + stylesStart, it) }
                }
                return StringPool(strings, flags, styleOffsets, styleData)
            }

            private fun readUtf8(buf: ByteBuffer, at: Int): String {
                var p = at
                p += if (buf.get(p).toInt() and 0x80 != 0) 2 else 1 // length in UTF-16 units
                var byteLength = buf.get(p).toInt() and 0xFF
                if (byteLength and 0x80 != 0) {
                    byteLength = ((byteLength and 0x7F) shl 8) or (buf.get(p + 1).toInt() and 0xFF)
                    p += 2
                } else {
                    p += 1
                }
                return ByteArray(byteLength).also { buf.get(p, it) }.toString(Charsets.UTF_8)
            }

            private fun readUtf16(buf: ByteBuffer, at: Int): String {
                var p = at
                var length = buf.getShort(p).toInt() and 0xFFFF
                p += 2
                if (length and 0x8000 != 0) {
                    length = ((length and 0x7FFF) shl 16) or (buf.getShort(p).toInt() and 0xFFFF)
                    p += 2
                }
                return String(CharArray(length) { buf.getChar(p + 2 * it) })
            }

            private fun writeUtf8Length(out: ByteArrayOutputStream, length: Int) {
                require(length <= 0x7FFF) { "string too long" }
                if (length > 0x7F) out.write(0x80 or (length ushr 8))
                out.write(length and 0xFF)
            }

            private fun writeUtf16Length(out: ByteArrayOutputStream, length: Int) {
                require(length <= 0x7FFF) { "string too long" }
                out.write(length and 0xFF)
                out.write(length ushr 8)
            }
        }
    }

    /** One attribute of a start element, edited in place in [Document.tree]. */
    private class Attribute(private val tree: ByteBuffer, private val at: Int) {
        val dataType get() = tree.get(at + 15).toInt() and 0xFF
        val data get() = tree.getInt(at + 16)

        fun setString(index: Int) {
            tree.putInt(at + 8, index) // rawValue
            tree.put(at + 15, TYPE_STRING.toByte())
            tree.putInt(at + 16, index)
        }
    }

    private class StartElement(val name: String, val attributes: List<Pair<Int, Attribute>>)

    /** The string pool, plus every chunk after it ([tree]), kept as bytes with chunk-relative edits. */
    private class Document(val pool: StringPool, val tree: ByteBuffer, val resourceIds: IntArray) {
        fun elements(): Sequence<StartElement> = sequence {
            var p = 0
            while (p + 8 <= tree.limit()) {
                val type = tree.getShort(p).toInt() and 0xFFFF
                val headerSize = tree.getShort(p + 2).toInt() and 0xFFFF
                val size = tree.getInt(p + 4)
                if (size < 8) throw ManifestFormatException("bad chunk size $size")
                if (type == RES_XML_START_ELEMENT_TYPE) {
                    val ext = p + headerSize
                    val name = pool.strings[tree.getInt(ext + 4)]
                    val attrStart = tree.getShort(ext + 8).toInt() and 0xFFFF
                    val attrSize = tree.getShort(ext + 10).toInt() and 0xFFFF
                    val attrCount = tree.getShort(ext + 12).toInt() and 0xFFFF
                    val attrs = (0 until attrCount).map {
                        val at = ext + attrStart + it * attrSize
                        tree.getInt(at + 4) to Attribute(tree, at)
                    }
                    yield(StartElement(name, attrs))
                }
                p += size
            }
        }

        fun packageName(): String {
            val manifest = elements().firstOrNull { it.name == "manifest" }
                ?: throw ManifestFormatException("no <manifest> element")
            val attr = manifest.attributes.firstOrNull { (name, _) -> pool.strings.getOrNull(name) == "package" }?.second
                ?: throw ManifestFormatException("<manifest> has no package attribute")
            if (attr.dataType != TYPE_STRING) throw ManifestFormatException("package is not a string")
            return pool.strings[attr.data]
        }

        fun applicationLabel(): Attribute? =
            elements().firstOrNull { it.name == "application" }
                ?.attributes?.firstOrNull { (name, _) -> resourceIds.getOrNull(name) == ATTR_LABEL }?.second

        fun encode(): ByteArray {
            val poolBytes = pool.encode()
            val size = 8 + poolBytes.size + tree.limit()
            return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(RES_XML_TYPE.toShort())
                putShort(8)
                putInt(size)
                put(poolBytes)
                put(tree.array(), 0, tree.limit())
            }.array()
        }

        companion object {
            fun parse(bytes: ByteArray): Document {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                if (bytes.size < 8 || (buf.getShort(0).toInt() and 0xFFFF) != RES_XML_TYPE) {
                    throw ManifestFormatException("not a binary XML file")
                }
                val poolAt = buf.getShort(2).toInt() and 0xFFFF
                if ((buf.getShort(poolAt).toInt() and 0xFFFF) != RES_STRING_POOL_TYPE) {
                    throw ManifestFormatException("string pool is not the first chunk")
                }
                val pool = StringPool.parse(buf, poolAt)
                val treeAt = poolAt + buf.getInt(poolAt + 4)
                val end = minOf(buf.getInt(4), bytes.size)
                val tree = ByteBuffer.wrap(bytes.copyOfRange(treeAt, end)).order(ByteOrder.LITTLE_ENDIAN)
                var resourceIds = IntArray(0)
                if (tree.limit() >= 8 && (tree.getShort(0).toInt() and 0xFFFF) == RES_XML_RESOURCE_MAP_TYPE) {
                    val headerSize = tree.getShort(2).toInt() and 0xFFFF
                    resourceIds = IntArray((tree.getInt(4) - headerSize) / 4) { tree.getInt(headerSize + 4 * it) }
                }
                return Document(pool, tree, resourceIds)
            }
        }
    }
}
