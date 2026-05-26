# Use the host user's UID/GID inside the dev container
export DEV_UID := `id -u`
export DEV_GID := `id -g`

set shell := ["bash", "-cu"]

# Show available recipes
default:
    @just --list

# Open an interactive shell in the dev container
shell:
    docker compose run --rm dev bash

# Run the full check (test + spotless + errorprone + nullaway + jacoco)
check:
    docker compose run --rm dev ./gradlew check

# Run tests only
test:
    docker compose run --rm dev ./gradlew test

# Apply spotless formatting
format:
    docker compose run --rm dev ./gradlew spotlessApply

# Build the fat shadowJar
shadow:
    docker compose run --rm dev ./gradlew shadowJar

# Build the native-image binary
native:
    docker compose run --rm dev ./gradlew nativeCompile

# Start the web app in JVM mode (background); http://127.0.0.1:8080/
web: shadow
    docker compose --profile web up -d web
    @echo "→ http://127.0.0.1:8080/"

# Start the web app as a native binary (background); http://127.0.0.1:8080/
web-native: native
    docker compose --profile web-native up -d web-native
    @echo "→ http://127.0.0.1:8080/"

# Stop any running web container
web-stop:
    -docker compose --profile web down web
    -docker compose --profile web-native down web-native

# Generate a 4-page sample PDF at build/test-data/sample.pdf
sample-pdf:
    docker compose run --rm dev ./gradlew createSamplePdf

# Auto-fix typos across the repo
typos-fix:
    docker compose run --rm dev typos --write-changes

# Report typos without auto-fixing
typos:
    docker compose run --rm dev typos

# Remove build outputs
clean:
    docker compose run --rm dev ./gradlew clean
