plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import com.android.build.api.dsl.ApkSigningConfig

android {
    namespace = "com.space.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.space.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // R8 on, with the kotlinx-serialization keep rules in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = releaseSigningConfig()
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs merged resources to inflate Compose UI in JVM tests.
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.logging.enabled", "true") }
        }
    }

    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.toVersion(25)
        targetCompatibility = org.gradle.api.JavaVersion.toVersion(25)
    }
}


kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui:1.8.3")
    implementation("androidx.compose.ui:ui-graphics:1.8.3")
    implementation("androidx.compose.foundation:foundation:1.8.3")
    implementation("androidx.compose.animation:animation:1.8.3")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Compose UI tests on the JVM (no emulator available in this environment).
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.8.3")
    testImplementation("androidx.test.ext:junit:1.2.1")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.3")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.3")
}

// androidx.activity pins androidx.tracing to 1.0.0 while androidx.test wants
// 1.1.0. AGP's consistent resolution turns that mismatch into a hard failure
// when Lint models the androidTest classpath, so align it project-wide.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.tracing") {
            useVersion("1.1.0")
        }
    }
}

/** Reads release signing from keystore.properties (kept local, never committed). */
private fun releaseSigningConfig(): ApkSigningConfig? {
    val properties = Properties()
    val keystoreFile = rootProject.file("keystore.properties")
    if (!keystoreFile.exists()) return null
    keystoreFile.inputStream().use { properties.load(it) }
    val storePath = rootProject.file(properties.getProperty("storeFile", ""))
    return if (storePath.exists()) {
        android.signingConfigs.create("release") {
            this.storeFile = storePath
            this.storePassword = properties.getProperty("storePassword", "")
            this.keyAlias = properties.getProperty("keyAlias", "")
            this.keyPassword = properties.getProperty("keyPassword", "")
        }
    } else null
}
