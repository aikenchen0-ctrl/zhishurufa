package com.osfans.trime.sdk

internal class SdkInitialization<T : Any> {
    private var owner: T? = null
    private var failure: Throwable? = null
    private var initialized = false

    val isInitialized: Boolean
        @Synchronized get() = initialized

    val value: T
        @Synchronized get() {
            failure?.let { throw it }
            return checkNotNull(owner) { "TrimeSdk.initialize(application) must be called first" }
        }

    @Synchronized
    fun initialize(
        value: T,
        setup: () -> Unit,
    ) {
        failure?.let { throw it }
        if (initialized) {
            check(owner === value) { "TrimeSdk is already initialized for another application" }
            return
        }
        check(owner == null) { "TrimeSdk initialization cannot be called recursively" }
        owner = value
        try {
            setup()
            initialized = true
        } catch (error: Throwable) {
            // 保留原始异常，阻止重复执行已经产生部分副作用的初始化。
            failure = error
            throw error
        }
    }
}
