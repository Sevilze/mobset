pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.10"
    id("org.jetbrains.kotlin.kapt") version "2.2.10"
    id("org.jetbrains.kotlin.multiplatform") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("com.android.application") version "8.10.1"
    id("com.android.library") version "8.10.1"
    id("org.jetbrains.compose") version "1.8.2"
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "SetMobile"
include(":composeApp", ":domain", ":data")

