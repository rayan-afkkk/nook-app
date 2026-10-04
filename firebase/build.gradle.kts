plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test-junit"))
}


tasks.test {
    useJUnit()
    // Tests share one emulator, so never run them in parallel.
    maxParallelForks = 1
    environment("NO_PROXY", "127.0.0.1,localhost")
    testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
