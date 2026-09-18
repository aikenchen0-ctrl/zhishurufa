/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:Suppress("UnstableApiUsage")

plugins {
    id("com.osfans.trime.app-convention")
}

android {
    namespace = "com.osfans.trime.host"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.osfans.trime"
        minSdk = 21
        targetSdk = 36
        versionCode = 20261101
        versionName = "3.3.13"
        resValue("bool", "trime_legacy_storage", "true")
    }

    buildFeatures {
        resValues = true
    }

    base {
        archivesName = "${android.defaultConfig.applicationId}-$buildVersionName"
    }

    splits.abi {
        isEnable = true
        isUniversalApk = false
        reset()
        (project.buildAbiOverride?.split(",") ?: Versions.supportedAbis).forEach { include(it) }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "trime_app_name", "@string/app_name_debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = project.signKeyFile?.let {
                signingConfigs.create("release") {
                    storeFile = it
                    storePassword = project.signKeyStorePwd
                    keyAlias = project.signKeyAlias
                    keyPassword = project.signKeyPwd
                }
            } ?: signingConfigs.getByName("debug")
            resValue("string", "trime_app_name", "@string/app_name_release")
        }
    }

    packaging.jniLibs.useLegacyPackaging = true
}

dependencies {
    implementation(project(":ime-sdk"))
}

tasks.register("calculateNativeCacheHash") {
    dependsOn(":ime-sdk:calculateNativeCacheHash")
}
