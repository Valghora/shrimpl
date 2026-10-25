package com.valghora.wrapper.core.build

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Activity launch modes the template supports (values of `android:launchMode`). */
enum class LaunchMode(val value: Int) {
    SINGLE_TASK(2),
    SINGLE_INSTANCE_PER_TASK(4),
}

/** The activity's `<layout>` window sizes, in dp. */
data class WindowLayout(val defaultWidth: Int, val defaultHeight: Int, val minWidth: Int, val minHeight: Int)

/** Changes to make to a compiled manifest; null or empty means "leave as is" (docs/UNIWRAP_SPEC.md R8.2). */
data class ManifestEdits(
    val packageName: String,
    val label: String? = null,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val removePermissions: Set<String> = emptySet(),
    val removeFeatures: Set<String> = emptySet(),
    val launchMode: LaunchMode? = null,
    val window: WindowLayout? = null,
    val multiInstance: Boolean? = null,
)

/** What a compiled manifest says, for checks and tests. */
data class ManifestInfo(
    val packageName: String,
    /** The literal label, or null if the label is a resource reference. */
    val literalLabel: String?,
    val versionCode: Int?,
    val versionName: String?,
    val permissions: List<String>,
    val features: List<String>,
    val launchMode: Int?,
    val window: WindowLayout?,
    val multiInstance: Boolean?,
    val strings: List<String>,
)

class ManifestFormatException(message: String) : RuntimeException(message)

/**
 * Reads and rewrites a compiled (binary XML) AndroidManifest.xml, the form it takes inside an APK.
 *
 * Renaming the package also renames every string that starts with the old package name followed by
 * ".": permissions and provider authorities derived from it would otherwise make a copy fail to
 * install next to another one (duplicate permission, conflicting provider). Edits rebuild the string
 * pool and change attribute values in place; removed elements drop out of the element tree, and
 * their strings stay in the pool, unused. Plain JVM code, so it runs in host tests and on the device.
 */
object ManifestPatcher {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val STRING_POOL_HEADER_SIZE = 28
    private const val SORTED_FLAG = 0x1
    private const val UTF8_FLAG = 0x100

    private const val TYPE_STRING = 0x03
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val COMPLEX_UNIT_DIP = 1

    private const val ATTR_LABEL = 0x01010001
    private const val ATTR_NAME = 0x01010003
    private const val ATTR_LAUNCH_MODE = 0x0101001d
    private const val ATTR_VALUE = 0x01010024
    private const val ATTR_MIN_WIDTH = 0x0101013f
    private const val ATTR_MIN_HEIGHT = 0x01010140
    private const val ATTR_VERSION_CODE = 0x0101021b
    private const val ATTR_VERSION_NAME = 0x0101021c
    private const val ATTR_DEFAULT_WIDTH = 0x010104f4
    private const val ATTR_DEFAULT_HEIGHT = 0x010104f5

    const val MULTI_INSTANCE_PROPERTY = "android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI"

    fun read(manifest: ByteArray): ManifestInfo {
        val doc = Document.parse(manifest)
        val manifestElement = doc.single("manifest")
        val label = doc.firstOrNull("application")?.attribute(ATTR_LABEL)
        val layout = doc.firstOrNull("layout")
        return ManifestInfo(
            packageName = doc.packageName(),
            literalLabel = label?.takeIf { it.dataType == TYPE_STRING }?.let { doc.pool.strings[it.data] },
            versionCode = manifestElement.attribute(ATTR_VERSION_CODE)?.data,
            versionName = manifestElement.attribute(ATTR_VERSION_NAME)
                ?.takeIf { it.dataType == TYPE_STRING }?.let { doc.pool.strings[it.data] },
            permissions = doc.names("uses-permission"),
            features = doc.names("uses-feature"),
            launchMode = doc.firstOrNull("activity")?.attribute(ATTR_LAUNCH_MODE)?.data,
            window = layout?.let {
                WindowLayout(
                    defaultWidth = it.dp(ATTR_DEFAULT_WIDTH),
                    defaultHeight = it.dp(ATTR_DEFAULT_HEIGHT),
                    minWidth = it.dp(ATTR_MIN_WIDTH),
                    minHeight = it.dp(ATTR_MIN_HEIGHT),
                )
            },
            multiInstance = doc.multiInstanceProperty()?.attribute(ATTR_VALUE)?.let { it.data != 0 },
            strings = doc.pool.strings.toList(),
        )
    }

    fun patch(manifest: ByteArray, edits: ManifestEdits): ByteArray {
        val doc = Document.parse(manifest)
        val strings = doc.pool.strings
        val oldPackage = doc.packageName()
        for (i in strings.indices) {
            val s = strings[i]
            if (s == oldPackage || s.startsWith("$oldPackage.")) strings[i] = edits.packageName + s.substring(oldPackage.length)
        }
        val manifestElement = doc.single("manifest")
        edits.label?.let { label ->
            val attr = doc.firstOrNull("application")?.attribute(ATTR_LABEL)
                ?: throw ManifestFormatException("<application> has no android:label")
            attr.setString(doc.addString(label))
        }
        edits.versionCode?.let { code ->
            require(code > 0) { "versionCode must be positive" }
            manifestElement.require(ATTR_VERSION_CODE, "android:versionCode").setValue(TYPE_INT_DEC, code)
        }
        edits.versionName?.let { name ->
            manifestElement.require(ATTR_VERSION_NAME, "android:versionName").setString(doc.addString(name))
        }
        edits.launchMode?.let { mode ->
            val activity = doc.firstOrNull("activity") ?: throw ManifestFormatException("no <activity>")
            activity.require(ATTR_LAUNCH_MODE, "android:launchMode").setValue(TYPE_INT_DEC, mode.value)
        }
        edits.window?.let { window ->
            val layout = doc.firstOrNull("layout") ?: throw ManifestFormatException("no <layout>")
            layout.require(ATTR_DEFAULT_WIDTH, "android:defaultWidth").setValue(TYPE_DIMENSION, dp(window.defaultWidth))
            layout.require(ATTR_DEFAULT_HEIGHT, "android:defaultHeight").setValue(TYPE_DIMENSION, dp(window.defaultHeight))
            layout.require(ATTR_MIN_WIDTH, "android:minWidth").setValue(TYPE_DIMENSION, dp(window.minWidth))
            layout.require(ATTR_MIN_HEIGHT, "android:minHeight").setValue(TYPE_DIMENSION, dp(window.minHeight))
        }
        edits.multiInstance?.let { enabled ->
            val property = doc.multiInstanceProperty() ?: throw ManifestFormatException("no multi-instance <property>")
            property.require(ATTR_VALUE, "android:value").setValue(TYPE_INT_BOOLEAN, if (enabled) -1 else 0)
        }
        val removals = doc.elements().filter { element ->
            (element.name == "uses-permission" && element.nameValue() in edits.removePermissions) ||
                (element.name == "uses-feature" && element.nameValue() in edits.removeFeatures)
        }.toList()
        return doc.encode(removedElements = removals)
    }

    /** Integer dp as a complex dimension: mantissa in bits 8 and up, radix 0, unit dip. */
    private fun dp(value: Int): Int {
        require(value in 1..0x7FFFFF) { "dimension out of range: $value" }
        return (value shl 8) or COMPLEX_UNIT_DIP
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
        val nameIndex get() = tree.getInt(at + 4)
        val dataType get() = tree.get(at + 15).toInt() and 0xFF
        val data get() = tree.getInt(at + 16)

        fun setString(index: Int) {
            tree.putInt(at + 8, index) // rawValue
            setValue(TYPE_STRING, index)
        }

        fun setValue(type: Int, value: Int) {
            if (type != TYPE_STRING) tree.putInt(at + 8, -1) // no raw string for typed values
            tree.put(at + 15, type.toByte())
            tree.putInt(at + 16, value)
        }
    }

    /** A start element: its chunk's position in [Document.tree] and its attributes. */
    private class Element(
        val doc: Document,
        val name: String,
        val offset: Int,
        val size: Int,
        val attributes: List<Attribute>,
    ) {
        fun attribute(resourceId: Int): Attribute? =
            attributes.firstOrNull { doc.resourceIds.getOrNull(it.nameIndex) == resourceId }

        fun require(resourceId: Int, label: String): Attribute =
            attribute(resourceId) ?: throw ManifestFormatException("<$name> has no $label")

        /** The value of android:name, if it is a string. */
        fun nameValue(): String? = attribute(ATTR_NAME)?.takeIf { it.dataType == TYPE_STRING }?.let { doc.pool.strings[it.data] }

        fun dp(resourceId: Int): Int {
            val attr = require(resourceId, "dimension 0x${resourceId.toString(16)}")
            if (attr.dataType != TYPE_DIMENSION || attr.data and 0xF != COMPLEX_UNIT_DIP || attr.data and 0x30 != 0) {
                throw ManifestFormatException("<$name> dimension is not an integer dp value")
            }
            return attr.data shr 8
        }
    }

    /** The string pool, plus every chunk after it ([tree]), kept as bytes with chunk-relative edits. */
    private class Document(val pool: StringPool, val tree: ByteBuffer, val resourceIds: IntArray) {
        fun addString(value: String): Int {
            pool.strings.add(value)
            return pool.strings.lastIndex
        }

        fun elements(): Sequence<Element> = sequence {
            var p = 0
            while (p + 8 <= tree.limit()) {
                val type = tree.getShort(p).toInt() and 0xFFFF
                val headerSize = tree.getShort(p + 2).toInt() and 0xFFFF
                val size = tree.getInt(p + 4)
                if (size < 8 || p + size > tree.limit()) throw ManifestFormatException("bad chunk size $size")
                if (type == RES_XML_START_ELEMENT_TYPE) {
                    val ext = p + headerSize
                    val name = pool.strings[tree.getInt(ext + 4)]
                    val attrStart = tree.getShort(ext + 8).toInt() and 0xFFFF
                    val attrSize = tree.getShort(ext + 10).toInt() and 0xFFFF
                    val attrCount = tree.getShort(ext + 12).toInt() and 0xFFFF
                    val attrs = (0 until attrCount).map { Attribute(tree, ext + attrStart + it * attrSize) }
                    yield(Element(this@Document, name, p, size, attrs))
                }
                p += size
            }
        }

        fun firstOrNull(name: String) = elements().firstOrNull { it.name == name }

        fun single(name: String): Element = elements().filter { it.name == name }.toList().singleOrNull()
            ?: throw ManifestFormatException("expected exactly one <$name>")

        fun names(element: String): List<String> = elements().filter { it.name == element }.mapNotNull { it.nameValue() }.toList()

        fun multiInstanceProperty(): Element? =
            elements().firstOrNull { it.name == "property" && it.nameValue() == MULTI_INSTANCE_PROPERTY }

        fun packageName(): String {
            val attr = single("manifest").attributes.firstOrNull { pool.strings.getOrNull(it.nameIndex) == "package" }
                ?: throw ManifestFormatException("<manifest> has no package attribute")
            if (attr.dataType != TYPE_STRING) throw ManifestFormatException("package is not a string")
            return pool.strings[attr.data]
        }

        /** Drops each removed element: its start chunk and the end chunk right after it (they are leaves). */
        fun encode(removedElements: List<Element> = emptyList()): ByteArray {
            val skip = mutableListOf<IntRange>()
            for (element in removedElements) {
                val end = element.offset + element.size
                val endType = tree.getShort(end).toInt() and 0xFFFF
                if (endType != RES_XML_END_ELEMENT_TYPE) {
                    throw ManifestFormatException("<${element.name}> has children; only leaf elements can be removed")
                }
                skip += element.offset until end + tree.getInt(end + 4)
            }
            val treeBytes = ByteArrayOutputStream()
            var p = 0
            for (range in skip.sortedBy { it.first }) {
                treeBytes.write(tree.array(), p, range.first - p)
                p = range.last + 1
            }
            treeBytes.write(tree.array(), p, tree.limit() - p)

            val poolBytes = pool.encode()
            val size = 8 + poolBytes.size + treeBytes.size()
            return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(RES_XML_TYPE.toShort())
                putShort(8)
                putInt(size)
                put(poolBytes)
                put(treeBytes.toByteArray())
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
