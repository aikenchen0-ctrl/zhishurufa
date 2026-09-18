plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.zhishurufa.sample"
    compileSdk = 36
    testBuildType = if (providers.gradleProperty("verifyRelease").orNull == "true") "release" else "debug"

    defaultConfig {
        applicationId = "com.zhishurufa.sample"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testProguardFiles("test-proguard-rules.pro")
        ndk {
            abiFilters += providers.gradleProperty("buildABI").orNull
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (providers.gradleProperty("verifyRelease").orNull == "true") {
                proguardFiles("verification-proguard-rules.pro")
                // 仅本机发布验证使用调试签名，正式发布仍由宿主提供签名。
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("com.google.errorprone:error_prone_annotations:2.27.0")
    androidTestImplementation(libs.kotlinx.coroutines)
    if (providers.gradleProperty("usePublishedSdk").orNull == "true") {
        val artifact = if (providers.gradleProperty("sdkPublication").orNull == "release") "ime-sdk-release" else "ime-sdk"
        implementation("com.zhishurufa:$artifact:0.1.0-SNAPSHOT")
    } else {
        implementation(project(":ime-sdk"))
    }
}
