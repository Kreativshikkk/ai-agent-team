plugins {
    kotlin("jvm")                   version "2.1.21"
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

tasks.register<Exec>("runPython") {
    group = "python"
    description = "Запускает src/main/python/team.py"
    // Если нужен конкретный интерпретатор — поменяй на full path или virtualenv/bin/python
    commandLine("python3.11", "$projectDir/src/main/python/team.py")
    // Если скрипт ждёт ввода или output большой, можно добавить:
    isIgnoreExitValue = false
}

tasks.wrapper { gradleVersion = "8.5" }
