plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val strawVersion: String = rootProject.file("VERSION").readText().trim()
val versionParts = strawVersion.split(".")
val computedVersionCode: Int =
    versionParts.getOrNull(0)?.toIntOrNull()?.times(1_000_000)
        ?.plus(versionParts.getOrNull(1)?.toIntOrNull()?.times(10_000) ?: 0)
        ?.plus(versionParts.getOrNull(2)?.toIntOrNull()?.times(100) ?: 0)
        ?.plus(versionParts.getOrNull(3)?.toIntOrNull() ?: 0)
        ?: 1

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
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.guava)
    implementation(libs.androidx.wear.watchface.complications.data.source)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
