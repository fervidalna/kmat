plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val appVersionName = "1.1"

android {
    namespace = "cl.kmat.ia"
    compileSdk = 35
    val signingStoreFile = providers.gradleProperty("K_MAT_SIGNING_STORE_FILE")
        .orElse(providers.environmentVariable("K_MAT_SIGNING_STORE_FILE")).orNull
    val signingStorePassword = providers.gradleProperty("K_MAT_SIGNING_STORE_PASSWORD")
        .orElse(providers.environmentVariable("K_MAT_SIGNING_STORE_PASSWORD")).orNull
    val signingKeyAlias = providers.gradleProperty("K_MAT_SIGNING_KEY_ALIAS")
        .orElse(providers.environmentVariable("K_MAT_SIGNING_KEY_ALIAS")).orNull
    val signingKeyPassword = providers.gradleProperty("K_MAT_SIGNING_KEY_PASSWORD")
        .orElse(providers.environmentVariable("K_MAT_SIGNING_KEY_PASSWORD")).orNull
    // Gradle properties take precedence for local development; CI can inject env vars.
    val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
        .orElse(providers.environmentVariable("SUPABASE_URL")).orElse("").get()
    val supabaseAnonKey = providers.gradleProperty("SUPABASE_ANON_KEY")
        .orElse(providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY"))
        .orElse(providers.environmentVariable("SUPABASE_ANON_KEY"))
        .orElse(providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY")).orElse("").get()
    val supabaseExerciseVersionId = providers.gradleProperty("SUPABASE_EXERCISE_VERSION_ID")
        .orElse(providers.environmentVariable("SUPABASE_EXERCISE_VERSION_ID")).orElse("").get()

    defaultConfig {
        applicationId = "cl.kmat.ia"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = appVersionName
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_EXERCISE_VERSION_ID", "\"$supabaseExerciseVersionId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (signingStoreFile != null && signingStorePassword != null &&
                signingKeyAlias != null && signingKeyPassword != null
            ) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(signingStoreFile)
                    storePassword = signingStorePassword
                    keyAlias = signingKeyAlias
                    keyPassword = signingKeyPassword
                }
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

// Give both locally built and release APKs a recognizable, versioned name.
android.applicationVariants.all {
    outputs.all {
        val apkOutput = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        apkOutput.outputFileName = "k-mat-$appVersionName.apk"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
