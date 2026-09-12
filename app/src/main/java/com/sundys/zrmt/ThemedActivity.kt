package com.sundys.zrmt

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

/**
 * 主题基类：按设置在 attachBaseContext 中覆盖本地夜间模式。
 * 颜色资源由 values / values-night 双套目录按 uiMode 自动解析。
 */
abstract class ThemedActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        val sysNight = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val wantNight = when (AppPrefs.themeMode(newBase)) {
            AppPrefs.THEME_DARK -> true
            AppPrefs.THEME_LIGHT -> false
            else -> sysNight
        }
        if (wantNight != sysNight) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (wantNight) Configuration.UI_MODE_NIGHT_YES
                else Configuration.UI_MODE_NIGHT_NO
        }
        applyOverrideConfiguration(config)
        super.attachBaseContext(newBase)
    }
}
