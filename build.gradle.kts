plugins {
    kotlin("jvm") version "2.2.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "de.ionnetwork"
version = "1.0.0"

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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}

kotlin {
    jvmToolchain(17)
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

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
