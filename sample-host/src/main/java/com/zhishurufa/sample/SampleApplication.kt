package com.zhishurufa.sample

import android.app.Application
import com.osfans.trime.sdk.TrimeSdk

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TrimeSdk.initialize(this)
    }
}
