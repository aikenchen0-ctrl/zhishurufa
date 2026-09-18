/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class NativeLibraryConventionPlugin : NativeBaseConventionPlugin() {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.library")
        super.apply(target)
        AndroidBaseConventionPlugin().apply(target)
        target.extensions.configure<LibraryExtension> {
            packaging {
                jniLibs.useLegacyPackaging = true
            }
            defaultConfig {
                buildConfigField("String", "LIBRIME_VERSION", "\"${target.librimeVersion}\"")
                buildConfigField("String", "OPENCC_VERSION", "\"${target.openccVersion}\"")
            }
        }
        target.extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants { variant ->
                val name = variant.name.replaceFirstChar { it.uppercase() }
                target.afterEvaluate {
                    target.tasks.matching {
                        it.name == "merge${name}Assets" || it.name == "package${name}Assets" ||
                            (it.name.contains(name) && (it.name.contains("Lint") || it.name.startsWith("lint")))
                    }.configureEach {
                        dependsOn(DataChecksumsPlugin.TASK)
                    }
                }
            }
        }
    }
}
