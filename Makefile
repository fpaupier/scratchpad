.PHONY: build run test package publish clean

## Build the plugin
build:
	./gradlew build -x buildSearchableOptions

## Launch a sandbox IDE with the plugin pre-loaded
run:
	./gradlew runIde

## Run tests
test:
	./gradlew test

## Package for distribution (zip)
package:
	./gradlew buildPlugin -x buildSearchableOptions

## Publish to JetBrains Marketplace
publish: build package
	./gradlew buildPlugin publishPlugin -x buildSearchableOptions

## Remove build artifacts
clean:
	./gradlew clean
