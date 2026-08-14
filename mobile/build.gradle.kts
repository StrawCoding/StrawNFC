import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

extra["strawFormFactor"] = 0 // phone — even Play versionCode
apply(from = rootProject.file("gradle/release-env.gradle.kts"))

val strawVersion: String = extra["strawVersionName"] as String
val computedVersionCode: Int = extra["strawVersionCode"] as Int
val hasReleaseSigning: Boolean = extra["strawHasReleaseSigning"] as Boolean

android {
    namespace = "xyz.wastebase.strawnfc.mobile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "xyz.wastebase.strawnfc"
        minSdk = libs.versions.minSdkMobile.get().toInt()
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.play.services.wearable)
    testImplementation(libs.junit)
}
