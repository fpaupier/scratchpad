.PHONY: build run test package publish clean

## Build the plugin
build:
	./gradlew build

## Launch a sandbox IDE with the plugin pre-loaded
run:
	./gradlew runIde

## Run tests
test:
	./gradlew test

## Package for distribution (zip)
package:
	./gradlew buildPlugin

## Publish to JetBrains Marketplace
publish:
	./gradlew publishPlugin

## Remove build artifacts
clean:
	./gradlew clean
