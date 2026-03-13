plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.13.0"
}

group = "com.alexandria"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
    }
}

kotlin {
    jvmToolchain(17)
}

// Load .env file if present
val dotenv = file(".env").takeIf { it.exists() }?.readLines()
    ?.filter { it.contains("=") && !it.startsWith("#") }
    ?.associate { line ->
        val (key, value) = line.split("=", limit = 2)
        key.trim() to value.trim()
    } ?: emptyMap()

tasks {
    patchPluginXml {
        sinceBuild.set("243")
        untilBuild.set("253.*")
    }

    publishPlugin {
        token.set(
            providers.environmentVariable("INTELLIJ_PUBLISH_TOKEN")
                .orElse(provider { dotenv["INTELLIJ_PUBLISH_TOKEN"] })
        )
    }
}
