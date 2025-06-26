import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "8.3.0"
}

group = "twizzy.tech"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("net.minestom:minestom-snapshots:0366b58bfe")

    implementation("dev.hollowcube:polar:1.13.0")

    implementation("com.github.TogAr2:MinestomPvP:dfb8f0c342")

    // Lamp Command Framework
    implementation("io.github.revxrsal:lamp.common:4.0.0-rc.4")
    implementation("io.github.revxrsal:lamp.minestom:4.0.0-rc.4")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.6.4")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-minestom-api:2.22.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-minestom-core:2.22.0")

    implementation("org.yaml:snakeyaml:2.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // Minestom has a minimum Java version of 21
    }
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "twizzy.tech.MainKt" // Change this to your main class
        }
    }

    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("") // Prevent the -all suffix on the shadowjar file.
    }
}
tasks.withType<JavaCompile> {
    // Preserve parameter names in the bytecode
    options.compilerArgs.add("-parameters")
}

// optional: if you're using Kotlin
tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        javaParameters = true
    }
}