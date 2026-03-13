# Scratchpad Plugin

JetBrains plugin that adds `@` file reference autocomplete in scratch files. Type `@` followed by a query to fuzzy-search project files and insert a relative path reference.

Works on all JetBrains IDEs (PyCharm, WebStorm, IntelliJ IDEA, etc.).

## Prerequisites

- Java 17+ (install via `brew install --cask temurin@17` or any JDK 17+ distribution)
- No need to install Gradle — the included wrapper (`./gradlew`) handles it

## Development

| Command        | Description                                        |
|----------------|----------------------------------------------------|
| `make build`   | Build the plugin                                   |
| `make run`     | Launch a sandbox IDE with the plugin pre-loaded     |
| `make test`    | Run tests                                          |
| `make package` | Package for distribution (zip)                     |
| `make publish` | Publish to JetBrains Marketplace                   |
| `make clean`   | Remove build artifacts                             |

### Manual testing

1. Run `make run` to launch the sandbox IDE
2. Open or create any project
3. Open a scratch file: **Cmd+Shift+N** (macOS) / **Ctrl+Shift+Alt+Insert** (Linux/Windows), choose Text or Markdown
4. Type `@` — the popup should show top-level project files immediately
5. Type `@docker` — narrows to `Dockerfile`, `docker-compose.yml`, etc.
6. Select a result — it inserts `@relative/path/to/file`
7. Also works in `.txt` and `.md` files
8. Type `@` in a regular project file (e.g. `.kt`) — nothing should happen

`make package` produces `build/distributions/scratchpad-plugin-<version>.zip`.

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
3. Copy the token

### 3. Configure the token

Copy the example env file and paste your token:

```bash
cp .env.example .env
```

Then edit `.env`:

```
INTELLIJ_PUBLISH_TOKEN=your-token-here
```

The `.env` file is gitignored. The build reads the token from `.env` automatically, or from the `INTELLIJ_PUBLISH_TOKEN` environment variable if set.

### 4. Update metadata before publishing

Edit `src/main/resources/META-INF/plugin.xml` to fill in:
- `<description>` — shown on the marketplace listing
- `<vendor>` — your name or organization, optionally with `email` and `url` attributes
- `<change-notes>` — changelog for each version

Bump the version in `build.gradle.kts` before each release.

### 5. Publish

```bash
make publish
```

The plugin will be uploaded and go through JetBrains review (typically 1-2 business days). Once approved, it appears on the [JetBrains Marketplace](https://plugins.jetbrains.com/).
