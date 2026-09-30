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
// Security: raises vulnerable transitive versions in the build tooling (AGP plugin classpath,
// Android lint, Unified Test Platform). None of these ship in the APK, but the CVE check
// (.github/workflows/security.yml) flags them. Only raises versions, never downgrades.
// Drop an entry once AGP brings a fixed version itself.
gradle.allprojects {
    val raiseVulnerableToolingVersions = Action<DependencyResolveDetails> {
        fun older(a: String, b: String): Boolean {
            val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val p = x.getOrElse(i) { 0 }
                val q = y.getOrElse(i) { 0 }
                if (p != q) return p < q
            }
            return false
        }
        val group = requested.group
        val name = requested.name
        val version = requested.version ?: return@Action
        val fixed = when {
            group == "io.netty" && version.startsWith("4.1.") -> "4.1.138.Final"
            group == "org.bouncycastle" && name.endsWith("-jdk18on") -> "1.86"
            group == "org.jdom" && name == "jdom2" -> "2.0.6.1"
            group == "org.bitbucket.b_c" && name == "jose4j" -> "0.9.7"
            group == "org.apache.commons" && name == "commons-lang3" -> "3.21.0"
            group == "org.apache.httpcomponents" && name == "httpclient" -> "4.5.14"
            else -> return@Action
        }
        if (older(version, fixed)) {
            useVersion(fixed)
            because("CVE fix in build tooling, see .github/workflows/security.yml")
        }
    }
    buildscript.configurations.configureEach { resolutionStrategy.eachDependency(raiseVulnerableToolingVersions) }
    configurations.configureEach { resolutionStrategy.eachDependency(raiseVulnerableToolingVersions) }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HRTracker"
include(":app")
