plugins {
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  id("org.jetbrains.kotlin.kapt") version "2.2.10"
  id("com.google.dagger.hilt.android") version "2.51.1"
}

android {
  namespace = "com.example.set.data"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  buildFeatures { buildConfig = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}


kotlin {
  androidTarget()
  // Use Java 21 toolchain for Kotlin compilation
  jvmToolchain(21)
}

dependencies {
  implementation(project(":domain"))

  implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
  implementation("com.google.firebase:firebase-auth-ktx")
  implementation("com.google.firebase:firebase-database-ktx")
  implementation("com.google.firebase:firebase-analytics-ktx")

  implementation("com.google.dagger:hilt-android:2.51.1")
  add("kapt", "com.google.dagger:hilt-android-compiler:2.51.1")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  implementation("androidx.datastore:datastore-preferences:1.1.1")
}

