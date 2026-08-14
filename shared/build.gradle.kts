plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val strawVersion: String = rootProject.file("VERSION").readText().trim()

android {
    namespace = "xyz.wastebase.strawnfc"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdkMobile.get().toInt()
        // Expose VERSION to consumers via BuildConfig field if needed later
        buildConfigField("String", "STRAW_VERSION", "\"$strawVersion\"")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
