import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

extra["strawFormFactor"] = 1 // wear — odd Play versionCode (same package as phone)
apply(from = rootProject.file("gradle/release-env.gradle.kts"))

val strawVersion: String = extra["strawVersionName"] as String
val computedVersionCode: Int = extra["strawVersionCode"] as Int
val hasReleaseSigning: Boolean = extra["strawHasReleaseSigning"] as Boolean

android {
    namespace = "xyz.wastebase.strawnfc.wear"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "xyz.wastebase.strawnfc"
        minSdk = libs.versions.minSdkWear.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = computedVersionCode
        versionName = strawVersion
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = extra["strawSigningStoreFile"] as File
                storePassword = extra["strawSigningStorePassword"] as String
                keyAlias = extra["strawSigningKeyAlias"] as String
                keyPassword = extra["strawSigningKeyPassword"] as String
                storeType = extra["strawSigningStoreType"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Play upload must be signed. assembleRelease may run unsigned for local/Hermes compile checks
// when keystore passwords are absent (see docs/play-release.md).
tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst {
        check(hasReleaseSigning) {
            "Release signing is not configured. Export ANDROID_KEYSTORE_FILE, " +
                "ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD " +
                "(see docs/play-release.md). Do not commit the keystore."
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.guava)
    implementation(libs.androidx.wear.watchface.complications.data.source)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
