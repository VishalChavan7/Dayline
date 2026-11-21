import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

/** Release signing details, kept out of git in keystore.properties (see keys/ on the build Mac). */
val releaseKey: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "com.dayline.app"
    compileSdk = 36

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = rootProject.file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.dayline.app"
        minSdk = 26
        targetSdk = 36
        // A fresh version code for every build (minutes since 2026-01-01), so Android and the home
        // screen never keep a cached icon or label from an earlier install.
        val buildStamp = ((System.currentTimeMillis() - 1_767_225_600_000L) / 60_000L).toInt().coerceAtLeast(2)
        versionCode = buildStamp
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            // Resource shrinking is off: its optimisations rewrote resources (inlined theme colours
            // into drawables) in ways some launchers handle badly. Code is still minified.
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with Dayline's own release key. Without keystore.properties (another machine)
            // it falls back to the debug key, which can't update an installed release build.
            signingConfig = if (releaseKey != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.coroutines.FlowPreview",
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.richeditor.compose)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    testImplementation(composeBom)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}


/**
 * Writes the signing certificate fingerprints Google needs for "Google sync" (an Android OAuth
 * client per package) to outputs/google-sync-setup.txt after every build.
 */
val writeGoogleSyncSetup by tasks.registering {
    doLast {
        val out = rootProject.file("outputs/google-sync-setup.txt")
        out.parentFile.mkdirs()
        fun fps(file: File, storePass: String, alias: String): String {
            if (!file.exists()) return "  (no keystore at $file)"
            val store = KeyStore.getInstance(if (file.name.endsWith(".jks")) "PKCS12" else KeyStore.getDefaultType())
            file.inputStream().use { store.load(it, storePass.toCharArray()) }
            val cert = store.getCertificate(alias)
            fun fp(alg: String) = MessageDigest.getInstance(alg).digest(cert.encoded).joinToString(":") { "%02X".format(it) }
            return "  SHA-1:   ${fp("SHA-1")}\n  SHA-256: ${fp("SHA-256")}"
        }
        val release = releaseKey?.let { fps(rootProject.file(it.getProperty("storeFile")), it.getProperty("storePassword"), it.getProperty("keyAlias")) }
            ?: "  (no keystore.properties: release builds use the debug key)"
        val debug = fps(File(System.getProperty("user.home"), ".android/debug.keystore"), "android", "androiddebugkey")
        out.writeText(
            "Google sync setup for Dayline\n\n" +
                "Release APK, package com.dayline.app:\n$release\n\n" +
                "Debug APK, package com.dayline.app.debug:\n$debug\n",
        )
    }
}
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach { finalizedBy(writeGoogleSyncSetup) }

