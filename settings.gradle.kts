// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        if (providers.gradleProperty("usePublishedSdk").orNull == "true") {
            maven { url = uri("build/sdk-repository") }
        }
    }
}

rootProject.name = "trime"
include(":app")
include(":codegen")
include(":ime-sdk")
include(":sample-host")
