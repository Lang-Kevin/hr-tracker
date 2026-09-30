pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// Path to a local checkout of shared-app-lib. Override with `sharedLibPath=...` in code/local.properties.
val sharedLibPath = java.util.Properties().apply {
    file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}.getProperty("sharedLibPath") ?: "../../shared-app-lib"
includeBuild(sharedLibPath)
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HRTracker"
include(":app")
