# Scratchpad Plugin

JetBrains plugin that adds `@` file reference autocomplete in scratch files. Type `@` followed by a query to fuzzy-search project files and insert a relative path reference.

Works on all JetBrains IDEs (PyCharm, WebStorm, IntelliJ IDEA, etc.).

## Prerequisites

- Java 17+ (install via `brew install --cask temurin@17` or any JDK 17+ distribution)
- No need to install Gradle — the included wrapper (`./gradlew`) handles it

## Development

### Run in a sandbox IDE

Launches a disposable IDE instance with the plugin pre-loaded:

```bash
./gradlew runIde
```

### Test manually

1. In the sandbox IDE, open or create any project
2. Open a scratch file: **Cmd+Shift+N** (macOS) / **Ctrl+Shift+Alt+Insert** (Linux/Windows), choose Text or Markdown
3. Type `@docker` — the popup should show `Dockerfile`, `docker-compose.yml`, etc.
4. Select a result — it inserts `@relative/path/to/file`
5. Type `@` in a regular project file — nothing should happen (scratch-only guard)

### Build

```bash
./gradlew build
```

### Package for distribution

```bash
./gradlew buildPlugin
```

Produces `build/distributions/scratchpad-plugin-<version>.zip`.

### Install from disk

1. Open your IDE → **Settings** → **Plugins**
2. Gear icon → **Install Plugin from Disk...**
3. Select `build/distributions/scratchpad-plugin-<version>.zip`
4. Restart the IDE

## Publishing to the JetBrains Plugin Marketplace

### 1. Create a JetBrains Hub account

Go to https://hub.jetbrains.com and sign up (or sign in with your JetBrains account).

### 2. Get a Marketplace upload token

1. Go to https://plugins.jetbrains.com/author/me/tokens
2. Click **Generate Token**
3. Copy the token — you'll need it to publish

### 3. Configure the token

Add the token to `~/.gradle/gradle.properties` (create the file if it doesn't exist):

```properties
intellijPublishToken=<your-token-here>
```

Or pass it as an environment variable:

```bash
export INTELLIJ_PUBLISH_TOKEN=<your-token-here>
```

### 4. Add publishing config to `build.gradle.kts`

Add this block:

```kotlin
tasks {
    publishPlugin {
        token.set(
            providers.gradleProperty("intellijPublishToken")
                .orElse(providers.environmentVariable("INTELLIJ_PUBLISH_TOKEN"))
        )
    }
}
```

### 5. Publish

```bash
./gradlew publishPlugin
```

The plugin will be uploaded and go through JetBrains review (typically 1-2 business days). Once approved, it appears on the [JetBrains Marketplace](https://plugins.jetbrains.com/).

### 6. Update metadata before publishing

Edit `src/main/resources/META-INF/plugin.xml` to fill in:
- `<description>` — shown on the marketplace listing
- `<vendor>` — your name or organization, optionally with `email` and `url` attributes
- `<change-notes>` — changelog for each version

Bump the version in `build.gradle.kts` before each release.
