package com.sundys.zrmt

import android.content.Context

/** 应用设置（SharedPreferences 存储）。 */
object AppPrefs {
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private const val FILE = "zrmt_settings"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun themeMode(context: Context): String =
        prefs(context).getString("theme_mode", THEME_SYSTEM) ?: THEME_SYSTEM

    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString("theme_mode", mode).apply()
    }

    fun githubProxy(context: Context): String =
        prefs(context).getString("github_proxy", "").orEmpty()

    fun setGithubProxy(context: Context, proxy: String) {
        prefs(context).edit().putString("github_proxy", proxy.trim()).apply()
    }
}
