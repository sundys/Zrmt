package com.sundys.zrmt

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SettingsActivity : ThemedActivity() {

    private var downloadCancelled = AtomicBoolean(false)
    private var pendingInstall: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // 当前版本
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { "?" }
        findViewById<TextView>(R.id.tvVersion).text =
            getString(R.string.current_version, versionName)

        // 主题颜色（弹出单选）
        val tvTheme = findViewById<TextView>(R.id.tvTheme)
        fun modeLabel(mode: String): String = getString(
            when (mode) {
                AppPrefs.THEME_LIGHT -> R.string.theme_light
                AppPrefs.THEME_DARK -> R.string.theme_dark
                else -> R.string.theme_system
            }
        )
        fun refreshThemeLabel() {
            tvTheme.text = getString(R.string.theme_current, modeLabel(AppPrefs.themeMode(this)))
        }
        refreshThemeLabel()
        findViewById<View>(R.id.rowTheme).setOnClickListener {
            val modes = listOf(AppPrefs.THEME_SYSTEM, AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK)
            val labels = modes.map { modeLabel(it) }.toTypedArray()
            val checked = modes.indexOf(AppPrefs.themeMode(this))
            AlertDialog.Builder(this)
                .setTitle(R.string.select_theme)
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    dialog.dismiss()
                    val mode = modes[which]
                    if (mode != AppPrefs.themeMode(this)) {
                        AppPrefs.setThemeMode(this, mode)
                        recreate()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // GitHub 代理前缀
        refreshProxyLabel()
        findViewById<View>(R.id.rowProxy).setOnClickListener { editProxy() }

        // 检测更新
        findViewById<View>(R.id.rowCheckUpdate).setOnClickListener { checkUpdate() }

        // 项目主页
        findViewById<View>(R.id.rowHome).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Updater.REPO}")))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户在系统设置里授予“安装未知应用”后返回，继续安装
        val pending = pendingInstall
        if (pending != null && packageManager.canRequestPackageInstalls()) {
            pendingInstall = null
            installApk(pending)
        }
    }

    private fun refreshProxyLabel() {
        val proxy = AppPrefs.githubProxy(this)
        findViewById<TextView>(R.id.tvProxy).text =
            if (proxy.isEmpty()) getString(R.string.proxy_unset) else proxy
    }

    private fun editProxy() {
        val input = EditText(this).apply {
            hint = getString(R.string.proxy_hint)
            setText(AppPrefs.githubProxy(this@SettingsActivity))
            val pad = (resources.displayMetrics.density * 20).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.github_proxy)
            .setMessage(R.string.proxy_message)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                AppPrefs.setGithubProxy(this, input.text.toString())
                refreshProxyLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setUpdateState(text: String) {
        findViewById<TextView>(R.id.tvUpdateState).text = text
    }

    private fun checkUpdate() {
        setUpdateState(getString(R.string.checking))
        thread {
            val info = Updater.fetchLatest(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (info == null) {
                    setUpdateState("")
                    Toast.makeText(this, R.string.check_failed, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val current = try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (_: Exception) { "0.0.0" }
                if (!Updater.isNewer(info.version, current)) {
                    setUpdateState("")
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.up_to_date_title, info.version))
                        .setMessage(getString(R.string.up_to_date_msg, current, info.via))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                    return@runOnUiThread
                }
                setUpdateState(getString(R.string.update_available, info.version))
                showUpdateDialog(info)
            }
        }
    }

    private fun showUpdateDialog(info: Updater.ReleaseInfo) {
        val notes = info.notes.takeIf { it.isNotBlank() } ?: getString(R.string.no_notes)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.found_new_version, info.version))
            .setMessage(notes + "\n\n" + getString(R.string.via_channel, info.via))
            .setPositiveButton(R.string.download_update) { _, _ -> startDownload(info) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startDownload(info: Updater.ReleaseInfo) {
        downloadCancelled = AtomicBoolean(false)
        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        val bar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvProgress = view.findViewById<TextView>(R.id.tvProgress)
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.downloading_title, info.version))
            .setView(view)
            .setNegativeButton(R.string.cancel) { _, _ -> downloadCancelled.set(true) }
            .setCancelable(false)
            .show()

        fun fmt(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> "%.1fMB".format(bytes / 1024f / 1024f)
            bytes >= 1024 -> "%.0fKB".format(bytes / 1024f)
            else -> "${bytes}B"
        }

        thread {
            val file = Updater.download(
                this,
                info.apkUrl,
                onProgress = { percent, done, total ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed || !dialog.isShowing) return@runOnUiThread
                        if (total > 0) {
                            bar.isIndeterminate = false
                            bar.max = 100
                            bar.progress = percent
                            tvProgress.text = "$percent%  ${fmt(done)} / ${fmt(total)}"
                        } else {
                            bar.isIndeterminate = true
                            tvProgress.text = fmt(done)
                        }
                    }
                },
                cancelled = { downloadCancelled.get() }
            )
            runOnUiThread {
                try { dialog.dismiss() } catch (_: Exception) {}
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (file != null) {
                    installApk(file)
                } else if (!downloadCancelled.get()) {
                    Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(file: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            pendingInstall = file
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
                Toast.makeText(this, R.string.install_perm_hint, Toast.LENGTH_LONG).show()
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.install_perm_hint, Toast.LENGTH_LONG).show()
            }
            return
        }
        val uri = Uri.parse("content://$packageName.apk/${file.name}")
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.install_failed, Toast.LENGTH_LONG).show()
        }
    }
}
