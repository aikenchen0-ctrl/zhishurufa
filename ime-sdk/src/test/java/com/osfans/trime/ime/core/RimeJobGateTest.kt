package com.osfans.trime.ime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class RimeJobGateTest : StringSpec({
    "remains busy until boundary is consumed" {
        val gate = RimeJobGate()
        val token = gate.begin()

        gate.complete(token)
        gate.isBusy().shouldBeTrue()

        gate.acknowledge(token)
        gate.isBusy().shouldBeFalse()
    }

    "old boundary cannot release new job" {
        val gate = RimeJobGate()
        val first = gate.begin()
        gate.complete(first)
        val second = gate.begin()

        gate.acknowledge(first)
        gate.isBusy().shouldBeTrue()

        gate.complete(second)
        gate.acknowledge(second)
        gate.isBusy().shouldBeFalse()
    }
})
