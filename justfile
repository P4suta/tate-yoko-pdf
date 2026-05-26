# Use the host user's UID/GID inside the dev container
export DEV_UID := `id -u`
export DEV_GID := `id -g`

set shell := ["bash", "-cu"]

# Show available recipes
default:
    @just --list

# Start a long-lived dev container (keeps the Gradle daemon warm across commands)
dev-up:
    docker compose --profile dev up -d dev-daemon
    @echo "→ dev-daemon up. Run 'just dev-down' to stop."

# Stop the long-lived dev container.
dev-down:
    -docker compose --profile dev down dev-daemon

# Run an arbitrary command inside dev-daemon (requires `just dev-up`).
exec *cmd="bash":
    docker compose exec dev-daemon {{cmd}}

# Dispatch into dev-daemon if running, else a one-shot `docker compose run --rm dev`
[private]
_run *args:
    @if [ -n "$(docker ps -q -f name=tate-yoko-pdf-dev-daemon)" ]; then \
        docker compose exec -T dev-daemon {{args}}; \
    else \
        docker compose run --rm dev {{args}}; \
    fi

# Open an interactive shell in the dev container
shell:
    @if [ -n "$(docker ps -q -f name=tate-yoko-pdf-dev-daemon)" ]; then \
        docker compose exec -it dev-daemon bash; \
    else \
        docker compose run --rm dev bash; \
    fi

# Run the full check (test + spotless + errorprone + nullaway + jacoco)
check:
    @just _run ./gradlew check

# Run tests only
test:
    @just _run ./gradlew test

# Apply spotless formatting
format:
    @just _run ./gradlew spotlessApply

# Build the fat shadowJar
shadow:
    @just _run ./gradlew shadowJar

# Build the native-image binary
native:
    @just _run ./gradlew nativeCompile

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
    @just _run ./gradlew createSamplePdf

# Auto-fix typos across the repo
typos-fix:
    @just _run typos --write-changes

# Report typos without auto-fixing
typos:
    @just _run typos

# Remove build outputs
clean:
    @just _run ./gradlew clean

# Report outdated deps (Gradle deps/plugins + Dockerfile/spotless/jacoco/security pins)
outdated:
    @just _run ./gradlew --console=plain --no-parallel --no-configuration-cache --warning-mode=none dependencyUpdates

# Pre-pull base image + build dev image + warm Gradle cache. Useful first-run / onboarding.
warmup:
    docker compose build dev
    @just _run ./gradlew --quiet help
    @echo "→ dev image built, Gradle cache primed."

# Reproduce CI's Linux native smoke (build + CLI + web upload/download) locally.
smoke: native
    @just _run bash -c '\
        ./build/native/nativeCompile/tate-yoko-pdf \
            build/test-data/sample.pdf \
            -o build/test-data/native-out.pdf \
        && test -s build/test-data/native-out.pdf \
        && file build/test-data/native-out.pdf | grep -q PDF \
        && echo "✓ CLI smoke passed"'

# Run JVM tests under the native-image agent to auto-generate reflect/proxy/resource config
trace:
    @just _run ./gradlew -Pagent test
    @echo "→ Agent config written under build/agent-config/. Diff it against META-INF/native-image/."
