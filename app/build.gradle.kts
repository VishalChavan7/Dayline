import java.security.KeyStore
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dayline.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dayline.app"
        minSdk = 26
        targetSdk = 36
        // A fresh version code for every build (minutes since 2026-01-01), so Android and the home
        // screen never keep a cached icon or label from an earlier install.
        val buildStamp = ((System.currentTimeMillis() - 1_767_225_600_000L) / 60_000L).toInt().coerceAtLeast(2)
        versionCode = buildStamp
        versionName = "1.1.5"
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
            // Signed with the local debug key so the optimized build can be side-loaded for testing.
            // Replace with a real signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
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
 * Writes the signing certificate fingerprint Google needs for "Google sync" (an Android OAuth
 * client for com.dayline.app) to outputs/google-sync-setup.txt after every debug build.
 */
val writeGoogleSyncSetup by tasks.registering {
    doLast {
        val ks = File(System.getProperty("user.home"), ".android/debug.keystore")
        val out = rootProject.file("outputs/google-sync-setup.txt")
        out.parentFile.mkdirs()
        if (!ks.exists()) { out.writeText("No debug keystore found at $ks\n"); return@doLast }
        val store = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.inputStream().use { store.load(it, "android".toCharArray()) }
        val cert = store.getCertificate("androiddebugkey")
        fun fp(alg: String) = MessageDigest.getInstance(alg).digest(cert.encoded).joinToString(":") { "%02X".format(it) }
        out.writeText(
            """
            Google sync setup for Dayline
            Package name (release APK): com.dayline.app
            Package name (debug APK):   com.dayline.app.debug
            SHA-1:   ${fp("SHA-1")}
            SHA-256: ${fp("SHA-256")}
            """.trimIndent() + "\n",
        )
    }
}
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach { finalizedBy(writeGoogleSyncSetup) }

