plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeCompiler)
  id("org.jetbrains.kotlin.kapt") version "2.2.10"
  id("com.google.dagger.hilt.android") version "2.51.1"
}

android {
  namespace = "com.example.set"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.example.set"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  buildFeatures {
    compose = true
  }


  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  // register the Android target (required for Kotlin Multiplatform Android support)
  androidTarget {
  }

  // Recommended: set the Kotlin toolchain to Java 21
  jvmToolchain(21)
}

dependencies {
  // Compose BOM
  val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.navigation:navigation-compose:2.8.2")

  implementation("com.google.dagger:hilt-android:2.51.1")
  add("kapt", "com.google.dagger:hilt-android-compiler:2.51.1")
  implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

  // Firebase
  implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
  implementation("com.google.firebase:firebase-auth-ktx")
  implementation("com.google.firebase:firebase-database-ktx")
  implementation("com.google.firebase:firebase-analytics-ktx")

  // DataStore
  implementation("androidx.datastore:datastore-preferences:1.1.1")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

  // Modules
  implementation(project(":domain"))
  implementation(project(":data"))

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.testExt.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
  correctErrorTypes = true
}