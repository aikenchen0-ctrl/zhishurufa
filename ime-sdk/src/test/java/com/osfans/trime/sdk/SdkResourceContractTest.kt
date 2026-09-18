package com.osfans.trime.sdk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SdkResourceContractTest : StringSpec({
    "SDK manifest uses namespaced critical resources" {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        manifest.contains("@xml/method") shouldBe false
        manifest.contains("@style/Theme.TrimeAppTheme") shouldBe false
        manifest.contains("@style/Theme.DialogTheme") shouldBe false
        manifest.contains("@xml/trime_method") shouldBe true
        manifest.contains("@style/Theme.TrimeSdkAppTheme") shouldBe true
        manifest.contains("@style/Theme.TrimeSdkDialogTheme") shouldBe true
    }
})
