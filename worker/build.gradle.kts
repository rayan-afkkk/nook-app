plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
}

kotlin {
    // The Worker itself: Kotlin compiled to an ES module that Cloudflare runs.
    js {
        nodejs()
        useEsModules()
        binaries.executable()
    }
    // Pure logic is also compiled for the JVM so the unit tests run without Node.
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
        }
    }
}

/** `./gradlew -p worker bundle` → build/worker/nook-worker.mjs, the file wrangler deploys. */
tasks.register<Sync>("bundle") {
    dependsOn("compileProductionExecutableKotlinJs")
    from(layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin"))
    into(layout.buildDirectory.dir("worker"))
}
