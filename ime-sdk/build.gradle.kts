/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.LibraryExtension

plugins {
    `maven-publish`
    id("com.osfans.trime.native-library-convention")
    id("com.osfans.trime.data-checksums")
    id("com.osfans.trime.native-cache-hash")
    id("com.osfans.trime.opencc-data")
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

group = "com.zhishurufa"
version = "0.1.0-SNAPSHOT"

configure<LibraryExtension> {
    namespace = "com.osfans.trime"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "BUILDER", "\"${project.builder}\"")
        buildConfigField("long", "BUILD_TIMESTAMP", project.buildTimestamp)
        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${project.buildCommitHash}\"")
        buildConfigField("String", "BUILD_GIT_REPO", "\"${project.buildGitRepo}\"")
        buildConfigField("String", "BUILD_VERSION_NAME", "\"${project.buildVersionName}\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    publishing {
        singleVariant("debug")
        singleVariant("release") {
            withSourcesJar()
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/*.version",
            "/META-INF/*.kotlin_module",
            "/META-INF/androidx/**",
            "/DebugProbesKt.bin",
            "/kotlin-tooling-metadata.json",
        )
    }
}

dependencies {
    ksp(project(":codegen"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.flexbox)
    implementation(libs.bravh)
    implementation(libs.timber)
    implementation(libs.xxpermissions)
    implementation(libs.kodein.di)
    implementation(libs.snakeyaml)
    implementation(libs.splitties.bitflags)
    implementation(libs.splitties.systemservices)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.coordinatorlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.aboutlibraries.core)
    implementation(libs.iconics.core)
    implementation(libs.community.material.typeface) {
        artifact { type = "aar" }
    }
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.junit)
}

// 从子模块复制真实方案，避免 Windows 将 Git 符号链接作为纯文本打包。
val rimeAssets = mapOf(
    "prelude" to listOf("default.yaml", "key_bindings.yaml", "punctuation.yaml", "symbols.yaml"),
    "luna-pinyin" to listOf(
        "luna_pinyin.dict.yaml", "luna_pinyin.schema.yaml", "luna_pinyin_fluency.schema.yaml",
        "luna_pinyin_simp.schema.yaml", "luna_pinyin_tw.schema.yaml", "luna_quanpin.schema.yaml", "pinyin.yaml",
    ),
    "stroke" to listOf("stroke.dict.yaml", "stroke.schema.yaml"),
    "essay" to listOf("essay.txt"),
)
val preparedAssets = layout.buildDirectory.dir("generated/imeAssets")
val prepareImeAssets = tasks.register<Sync>("prepareImeAssets") {
    dependsOn(OpenCCDataPlugin.INSTALL_TASK)
    into(preparedAssets)
    from("src/main/assets") {
        exclude("checksums.json")
        exclude(rimeAssets.values.flatten().map { "shared/$it" })
    }
    rimeAssets.forEach { (module, names) ->
        from(rootProject.file("app/data/rime/$module")) {
            include(names)
            into("shared")
        }
    }
}
extensions.getByType<LibraryExtension>().sourceSets.getByName("main").assets.setSrcDirs(listOf(preparedAssets))
tasks.named<DataChecksumsPlugin.DataChecksumsTask>(DataChecksumsPlugin.TASK) {
    dependsOn(prepareImeAssets)
    inputDir.set(preparedAssets)
    outputFile.set(preparedAssets.map { it.file(DataChecksumsPlugin.FILE_NAME) })
}

aboutLibraries {
    collect {
        configPath.set(rootProject.file("app/licenses"))
        fetchRemoteLicense.set(false)
        fetchRemoteFunding.set(false)
        includePlatform.set(false)
    }
    export {
        excludeFields.set(setOf("generated", "developers", "organization", "scm", "funding", "content"))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("debug") {
                from(components["debug"])
                artifactId = "ime-sdk"
            }
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "ime-sdk-release"
            }
        }
        repositories {
            maven {
                name = "localSdk"
                url = uri(rootProject.layout.buildDirectory.dir("sdk-repository"))
            }
        }
    }
}
