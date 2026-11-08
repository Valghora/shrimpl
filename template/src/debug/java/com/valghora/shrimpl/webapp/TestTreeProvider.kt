package com.valghora.shrimpl.webapp

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * A folder on disk (`cache/test-tree`) served as a document tree, like Android's storage provider does:
 * what the emulator tests hand the template as the folder a user picked (T6.18), so its listing,
 * create, delete, rename and move go through the real `DocumentsContract`. Debug builds only; document
 * IDs are paths below the root, "/" being the root itself.
 */
class TestTreeProvider : DocumentsProvider() {
    private lateinit var base: File

    override fun onCreate(): Boolean {
        base = File(requireNotNull(context).cacheDir, TREE_DIR).apply { mkdirs() }
        return true
    }

    private fun file(documentId: String): File = if (documentId == ROOT_ID) base else File(base, documentId.removePrefix("/"))

    private fun idOf(file: File): String = if (file == base) ROOT_ID else "/" + file.relativeTo(base).path

    private fun addRow(cursor: MatrixCursor, documentId: String) {
        val file = file(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        val flags = Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or Document.FLAG_SUPPORTS_MOVE or
            (if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else 0)
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, if (file == base) TREE_DIR else file.name)
            .add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream")
            .add(Document.COLUMN_FLAGS, flags)
            .add(Document.COLUMN_SIZE, if (file.isDirectory) 0L else file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    override fun queryRoots(projection: Array<String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID)).apply {
            newRow().add(Root.COLUMN_ROOT_ID, "test").add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE).add(Root.COLUMN_TITLE, TREE_DIR).add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
        }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor =
        MatrixCursor(projection ?: DEFAULT_COLUMNS).also { addRow(it, documentId) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_COLUMNS)
        file(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { addRow(cursor, idOf(it)) }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val created = File(file(parentDocumentId), displayName)
        val ok = if (mimeType == Document.MIME_TYPE_DIR) created.mkdirs() else created.createNewFile()
        if (!ok) throw FileNotFoundException("$displayName can't be created")
        return idOf(created)
    }

    override fun deleteDocument(documentId: String) {
        if (!file(documentId).deleteRecursively()) throw FileNotFoundException(documentId)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val source = file(documentId)
        val target = File(source.parentFile, displayName)
        if (!source.renameTo(target)) throw FileNotFoundException("$documentId can't be renamed")
        return idOf(target)
    }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String {
        val source = file(sourceDocumentId)
        val target = File(file(targetParentDocumentId), source.name)
        if (!source.renameTo(target)) throw FileNotFoundException("$sourceDocumentId can't be moved")
        return idOf(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        file(documentId).canonicalPath.startsWith(file(parentDocumentId).canonicalPath + File.separator) || parentDocumentId == documentId

    companion object {
        const val TREE_DIR = "test-tree"
        const val ROOT_ID = "/"

        /** The provider's authority in an app with package [packageName]. */
        fun authority(packageName: String) = "$packageName.testtree"

        private val DEFAULT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
