package com.osfans.trime.ime.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 记录 Rime 作业与主线程边界确认，只有两者都完成才允许编辑器写回。
 */
internal class RimeJobGate {
    private val nextId = AtomicLong()
    private val jobs = ConcurrentHashMap<Long, Boolean>()

    fun begin(): Long = nextId.incrementAndGet().also { jobs[it] = false }

    fun complete(id: Long) {
        jobs.computeIfPresent(id) { _, _ -> true }
    }

    fun acknowledge(id: Long) {
        if (jobs[id] == true) jobs.remove(id, true)
    }

    fun cancelIfNotCompleted(id: Long) {
        if (jobs[id] == false) jobs.remove(id, false)
    }

    fun isBusy(): Boolean = jobs.isNotEmpty()
}
