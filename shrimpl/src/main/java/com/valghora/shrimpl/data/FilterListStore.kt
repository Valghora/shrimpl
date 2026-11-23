package com.valghora.shrimpl.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.valghora.shrimpl.container
import com.valghora.shrimpl.core.adblock.FilterEngine
import com.valghora.shrimpl.core.adblock.FilterList
import com.valghora.shrimpl.core.records.WebAppRecord
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ad blocking's lists on Shrimpl's side (docs/SHRIMPL_SPEC.md R15): the last good copy of each list in
 * `files/adblock/`, with what the next download needs. Web apps read the copies through
 * [AppOptionsProvider]; they never download a list themselves.
 */
class FilterListStore(context: Context) {
    private val dir = File(context.filesDir, "adblock")

    /** What is known about a stored list: its version for web apps, and the download's validators. */
    data class Meta(val version: Long, val rules: Int, val etag: String?, val lastModified: String?)

    /** The stored copy of [list], or null before its first good download. */
    fun file(list: FilterList): File? = File(dir, "${list.id}.txt").takeIf { it.exists() && meta(list) != null }

    /** The version web apps compare against their own copy: when the copy was saved, in milliseconds. */
    fun version(list: FilterList): Long = meta(list)?.version ?: 0L

    fun meta(list: FilterList): Meta? = try {
        val lines = AtomicFile(File(dir, "${list.id}.meta")).readFully().decodeToString().lines()
        Meta(lines[0].toLong(), lines[1].toInt(), lines[2].ifEmpty { null }, lines[3].ifEmpty { null })
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    /**
     * Downloads [list] if it changed (R15.2): a conditional GET, so an unchanged list costs a 304. A new
     * copy replaces the last good one only when [FilterList.accept] does; true when the stored copy is
     * current afterwards.
     */
    suspend fun refresh(list: FilterList): Boolean = withContext(Dispatchers.IO) {
        val previous = meta(list)
        val connection = URL(list.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (previous != null) {
                previous.etag?.let { connection.setRequestProperty("If-None-Match", it) }
                previous.lastModified?.let { connection.setRequestProperty("If-Modified-Since", it) }
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> previous != null
                HttpURLConnection.HTTP_OK -> {
                    if (connection.url.protocol != "https") throw IOException("${list.id} was redirected off https")
                    val text = connection.inputStream.use { read(it) }.decodeToString()
                    val rules = FilterEngine.countRules(text)
                    if (!FilterList.accept(rules, previous?.rules)) {
                        Log.w(TAG, "Kept the last ${list.id}: the download had $rules rules (before: ${previous?.rules})")
                        previous != null
                    } else {
                        dir.mkdirs()
                        write(File(dir, "${list.id}.txt"), text.toByteArray())
                        val meta = listOf(
                            System.currentTimeMillis().toString(),
                            rules.toString(),
                            connection.getHeaderField("ETag").orEmpty(),
                            connection.getHeaderField("Last-Modified").orEmpty(),
                        )
                        write(File(dir, "${list.id}.meta"), meta.joinToString("\n").toByteArray())
                        true
                    }
                }
                else -> previous != null
            }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't download ${list.id}: ${e.message}")
            previous != null
        } finally {
            connection.disconnect()
        }
    }

    private fun read(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            if (out.size() + n > MAX_BYTES) throw IOException("A list larger than ${MAX_BYTES / 1024 / 1024} MB")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: IOException) {
            atomic.failWrite(out)
            throw e
        }
    }

    companion object {
        private const val TAG = "Shrimpl"
        private const val USER_AGENT = "Shrimpl"
        private const val TIMEOUT_MS = 30_000

        /** EasyList is about 2 MB; ten times that is a wrong file, not a list. */
        private const val MAX_BYTES = 20 * 1024 * 1024

        /** The lists Shrimpl keeps: those some app's ad blocking level uses (R15.1). */
        fun needed(records: List<WebAppRecord>): Set<FilterList> =
            records.flatMapTo(sortedSetOf()) { FilterList.forLevel(it.profile.adBlocking) }
    }
}

/** Downloads the lists some app uses (R15.2): daily while any does, and once at once when one starts. */
class FilterListWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val lists = FilterListStore.needed(container.store.records.value)
        val current = lists.map { container.filterLists.refresh(it) }
        return if (current.all { it }) Result.success() else Result.retry()
    }
}

/** Schedules [FilterListWorker] from what the records need (R15.2). */
object FilterListSchedule {
    private const val DAILY = "filter-lists"
    private const val NOW = "filter-lists-now"

    /** Daily downloads while [needed] has lists, none otherwise; [missing] lists are fetched at once. */
    fun apply(context: Context, needed: Set<FilterList>, missing: Boolean) {
        val work = WorkManager.getInstance(context)
        if (needed.isEmpty()) {
            work.cancelUniqueWork(DAILY)
            return
        }
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val daily = PeriodicWorkRequestBuilder<FilterListWorker>(1, TimeUnit.DAYS).setConstraints(online).build()
        work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)
        if (missing) {
            val now = OneTimeWorkRequestBuilder<FilterListWorker>().setConstraints(online).build()
            work.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, now)
        }
    }
}
