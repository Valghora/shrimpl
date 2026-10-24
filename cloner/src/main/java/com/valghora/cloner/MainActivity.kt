package com.valghora.cloner

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

/** Proof of concept UI: clone the bundled template APK under a new package name and install it. */
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var create: Button
    private val template by lazy { File(filesDir, "template.apk") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        create = findViewById(R.id.create)
        val label = findViewById<EditText>(R.id.label)
        val packageField = findViewById<EditText>(R.id.package_name)

        assets.open(TEMPLATE_ASSET).use { input -> template.outputStream().use { input.copyTo(it) } }
        val info = packageManager.getPackageArchiveInfo(template.path, 0)
        val appInfo = info?.applicationInfo
        val templateLabel = appInfo?.let {
            it.sourceDir = template.path
            it.publicSourceDir = template.path
            it.loadLabel(packageManager).toString()
        } ?: "?"
        val templatePackage = info?.packageName ?: "?"
        findViewById<TextView>(R.id.template).text = getString(R.string.template_info, templateLabel, templatePackage)
        if (savedInstanceState == null) {
            label.setText(getString(R.string.default_label, templateLabel))
            packageField.setText(getString(R.string.default_package, templatePackage))
        }

        create.setOnClickListener {
            val pkg = packageField.text.toString().trim()
            if (!PACKAGE_NAME.matches(pkg)) {
                status.setText(R.string.invalid_package)
                return@setOnClickListener
            }
            if (!packageManager.canRequestPackageInstalls()) {
                status.setText(R.string.allow_installs)
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return@setOnClickListener
            }
            buildAndInstall(pkg, label.text.toString().trim().ifEmpty { null })
        }
    }

    private fun buildAndInstall(pkg: String, label: String?) {
        create.isEnabled = false
        status.text = getString(R.string.status_building, pkg)
        worker.execute {
            try {
                val started = System.nanoTime()
                val output = File(cacheDir, "clone.apk")
                val (key, certificates) = SigningKey.getOrCreate()
                ApkCloner.clone(template, output, pkg, label, key, certificates)
                val ms = (System.nanoTime() - started) / 1_000_000
                main.post { status.text = getString(R.string.status_installing, ms, output.length() / 1024) }
                install(output)
            } catch (e: Exception) {
                main.post { finish(getString(R.string.status_failed, e.toString())) }
            }
        }
    }

    /** Hands the APK to the system installer; the result comes back to [onNewIntent]. */
    private fun install(apk: File) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(this, MainActivity::class.java).setAction(ACTION_INSTALL_STATUS)
            // Mutable: the installer adds the status extras.
            val pending = PendingIntent.getActivity(
                this, sessionId, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != ACTION_INSTALL_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let(::startActivity)
            PackageInstaller.STATUS_SUCCESS ->
                finish(getString(R.string.status_installed, intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)))
            else ->
                finish(getString(R.string.status_failed, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
    }

    private fun finish(message: String) {
        status.text = message
        create.isEnabled = true
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val TEMPLATE_ASSET = "template.apk"
        const val ACTION_INSTALL_STATUS = "com.valghora.cloner.INSTALL_STATUS"
        val PACKAGE_NAME = Regex("""[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+""")
    }
}
