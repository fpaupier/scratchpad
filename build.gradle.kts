plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.alexandria"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1")
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
        untilBuild.set("263.*")
    }

    publishPlugin {
        token.set(
            providers.environmentVariable("INTELLIJ_PUBLISH_TOKEN")
                .orElse(provider { dotenv["INTELLIJ_PUBLISH_TOKEN"] })
        )
    }
}
