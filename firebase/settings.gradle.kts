// Standalone build for the security-rules tests. Run inside the Firebase emulators (see README).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "nook-rules-tests"
