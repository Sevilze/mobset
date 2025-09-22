plugins {
  id("org.jetbrains.kotlin.jvm") version "2.2.10"
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  testImplementation(libs.junit)
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

