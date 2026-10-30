plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
}

// Keep generated intermediates outside a file-synced checkout when requested.
providers.gradleProperty("botBuildRoot").orNull?.let { root ->
    layout.buildDirectory.set(file(root).resolve("root"))
    subprojects { layout.buildDirectory.set(rootProject.file(root).resolve(name)) }
}
