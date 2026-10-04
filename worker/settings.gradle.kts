// Standalone build for the Cloudflare Worker (Kotlin/JS). Not part of the Android app build.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "nook-worker"
