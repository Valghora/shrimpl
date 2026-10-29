package com.valghora.shrimple.install

import com.valghora.shrimple.data.InstallMethod
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Whether an install method can be used right now (docs/SHRIMPLE_SPEC.md R6.2). */
enum class Availability {
    AVAILABLE,

    /** Shizuku is running but hasn't granted Shrimple permission yet. */
    NEEDS_PERMISSION,
    UNAVAILABLE,
}

sealed interface InstallResult {
    data object Success : InstallResult

    /** Session installs need "Install unknown apps" for Shrimple first (R6.3). */
    data object NeedsUnknownSources : InstallResult

    /** [methodUnavailable]: Shizuku stopped or root was denied, so the system installer is worth offering (R6.2). */
    data class Failure(val message: String, val methodUnavailable: Boolean = false) : InstallResult
}

/** One way to install and uninstall web apps (R6.1). */
interface Installer {
    val method: InstallMethod

    suspend fun availability(): Availability

    suspend fun install(apk: File): InstallResult

    suspend fun uninstall(packageName: String): InstallResult
}

/** Shared by the shell-based installers: runs `pm`, feeding stdin, and reads its result. */
internal object PmShell {
    private const val TIMEOUT_MINUTES = 5L

    /** Output of [command] with [stdin] piped in, as "exit=N" plus output (the shape IShellService returns). */
    fun run(command: List<String>, stdin: InputStream? = null): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        stdin?.use { input -> process.outputStream.use { input.copyTo(it) } } ?: process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return "exit=-1\ntimed out"
        }
        return "exit=${process.exitValue()}\n$output"
    }

    /** "exit=0\nSuccess" -> Success; anything else -> Failure with pm's message. */
    fun result(output: String): InstallResult {
        val exit = output.substringBefore('\n').removePrefix("exit=").toIntOrNull()
        val text = output.substringAfter('\n').trim()
        return if (exit == 0 && text.lines().any { it.trim() == "Success" }) {
            InstallResult.Success
        } else {
            InstallResult.Failure(text.ifEmpty { "pm exited with $exit" })
        }
    }

    fun installCommand(size: Long) = listOf("pm", "install", "-r", "-S", size.toString())

    fun uninstallCommand(packageName: String): List<String> {
        require(PACKAGE.matches(packageName)) { "invalid package name" }
        return listOf("pm", "uninstall", packageName)
    }

    private val PACKAGE = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
}
