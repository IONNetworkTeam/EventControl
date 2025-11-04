plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    alias(libs.plugins.kotlinPluginSerialization)
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "spigotmc"
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

dependencies {
    // Spigot API for compatibility with 1.8+
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")

    // Kotlinx serialization for JSON
    implementation(libs.kotlinxSerialization)

    // Project dependencies
    implementation(project(":utils"))
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("EventControl")

        // Relocate Kotlin and kotlinx libraries to avoid conflicts
        relocate("kotlin", "de.ionnetwork.eventcontrol.libs.kotlin")
        relocate("kotlinx", "de.ionnetwork.eventcontrol.libs.kotlinx")
    }

    build {
        dependsOn(shadowJar)
    }
}
