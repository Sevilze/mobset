// Top-level build file
plugins {
  // Versions pinned via settings pluginManagement; we apply in subprojects
}

tasks.register("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}

