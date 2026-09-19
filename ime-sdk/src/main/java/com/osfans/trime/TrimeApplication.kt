/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.osfans.trime.sdk.TrimeSdk
import com.osfans.trime.ui.main.LogActivity
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * Custom Application class.
 * Application class will only be created once when the app run,
 * so you can init a "global" class here, whose methods serve other
 * classes everywhere.
 */
class TrimeApplication : Application() {
    val coroutineScope get() = TrimeSdk.coroutineScope

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) {
            Thread.setDefaultUncaughtExceptionHandler { _, e ->
                val crashTime = System.currentTimeMillis()
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val lastCrashTimePrefKey = "last_crash_time"
                val lastCrashTime = sharedPrefs.getLong(lastCrashTimePrefKey, -1L)
                sharedPrefs.edit(commit = true) {
                    putLong(lastCrashTimePrefKey, crashTime)
                }
                if (crashTime - lastCrashTime <= 10_000L) {
                    // continuous crashes within 10 seconds, maybe in a crash loop. just bail
                    exitProcess(10)
                }
                startActivity(
                    Intent(applicationContext, LogActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(LogActivity.FROM_CRASH, true)
                        // avoid transaction overflow
                        val truncated =
                            e.stackTraceToString().let {
                                if (it.length > MAX_STACKTRACE_SIZE) {
                                    it.take(MAX_STACKTRACE_SIZE) + "<truncated>"
                                } else {
                                    it
                                }
                            }
                        putExtra(LogActivity.CRASH_STACK_TRACE, truncated)
                    },
                )
                exitProcess(10)
            }
        }
        instance = this
        if (BuildConfig.DEBUG) {
            Timber.plant(
                object : Timber.DebugTree() {
                    override fun createStackElementTag(element: StackTraceElement): String = "${super.createStackElementTag(element)}|${element.fileName}:${element.lineNumber}"

                    override fun log(
                        priority: Int,
                        tag: String?,
                        message: String,
                        t: Throwable?,
                    ) {
                        super.log(
                            priority,
                            "[${Thread.currentThread().name}] ${tag?.substringBefore('|')}",
                            "${tag?.substringAfter('|')}] $message",
                            t,
                        )
                    }
                },
            )
        } else {
            Timber.plant(
                object : Timber.Tree() {
                    override fun log(
                        priority: Int,
                        tag: String?,
                        message: String,
                        t: Throwable?,
                    ) {
                        if (priority < Log.INFO) return
                        Log.println(priority, "[${Thread.currentThread().name}]", message)
                    }
                },
            )
        }
        TrimeSdk.initialize(
            this,
            "com.osfans.trime.MainLauncherAlias",
            "${packageName}_preferences",
        )
    }

    companion object {
        private var instance: TrimeApplication? = null

        fun getInstance() = instance ?: throw IllegalStateException("Trime application is not created!")

        fun getLastPid() = TrimeSdk.lastPid

        private const val MAX_STACKTRACE_SIZE = 128000

        /**
         * This permission is requested by com.android.shell, makes it possible to start
         * deploy from `adb shell am` command:
         * ```sh
         * adb shell am broadcast -a com.osfans.trime.action.DEPLOY
         * ```
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-7.0.0_r1/packages/Shell/AndroidManifest.xml#67
         *
         * other candidate: android.permission.TEST_INPUT_METHOD requires Android 14
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/packages/Shell/AndroidManifest.xml#628
         */
        const val PERMISSION_TEST_INPUT_METHOD = "android.permission.READ_INPUT_STATE"
    }
}
