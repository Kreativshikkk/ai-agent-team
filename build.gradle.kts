plugins {
    kotlin("jvm")                   version "2.0.20"
    kotlin("plugin.serialization")  version "2.0.20"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.example"
version = "1.0.0"

kotlin { jvmToolchain(21) }

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    intellijPlatform { intellijIdeaCommunity("2025.1") }
}

tasks.wrapper { gradleVersion = "8.5" }
