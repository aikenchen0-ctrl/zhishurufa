/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

open class NativeBaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.configure<CommonExtension> {
            ndkVersion = target.ndkVersion
            defaultConfig.apply {
                @Suppress("UnstableApiUsage")
                externalNativeBuild {
                    cmake {
                        arguments("-DANDROID_STL=c++_static")
                    }
                }
            }
            // Use prebuilt JNI library if the "app/prebuilt" exists
            //
            // Steps to generate the prebuilt directory:
            // $ ./gradlew app:assembleRelease
            // $ cp --recursive app/build/intermediates/stripped_native_libs/universalRelease/out/lib app/prebuilt
            if (target.rootProject.file("app/prebuilt").exists()) {
                sourceSets.getByName("main").jniLibs.directories.add(target.rootProject.file("app/prebuilt").path)
            } else {
                externalNativeBuild.apply {
                    cmake {
                        version = target.cmakeVersion
                        path = target.nativeSourceDir.resolve("CMakeLists.txt")
                    }
                }
            }

            defaultConfig.ndk {
                abiFilters += target.buildAbiOverride?.split(",") ?: Versions.supportedAbis
            }
        }
        registerCleanCxxTask(target)
    }

    private fun registerCleanCxxTask(project: Project) {
        project
            .tasks.register<Delete>("cleanCxxIntermediates") {
                delete(project.file(".cxx"))
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }
}
