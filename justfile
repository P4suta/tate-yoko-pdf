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
    -docker compose --profile dev down dev-daemon --remove-orphans

# Run an arbitrary command inside dev-daemon (requires `just dev-up`).
exec *cmd="bash":
    docker compose exec dev-daemon {{cmd}}

# Dispatch into dev-daemon if running, else a one-shot `docker compose run --rm dev`.
# Also the integration point for lefthook hooks, so they pick up `dev-daemon` when
# the user has run `just dev-up`.
dev-run *args:
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
    @just dev-run ./gradlew check

# Run tests only
test:
    @just dev-run ./gradlew test

# Apply spotless formatting
format:
    @just dev-run ./gradlew spotlessApply

# Build the fat shadowJar
shadow:
    @just dev-run ./gradlew shadowJar

# Build the jpackage app-image (bundled JRE + shadow jar) under build/dist-jpackage/
package:
    @just dev-run ./gradlew jpackageImage

# SvelteKit dev server (Vite HMR on http://127.0.0.1:5173, /api & /ws proxied to :8080)
frontend-dev:
    @just dev-run bash -c 'cd frontend && corepack pnpm run dev --host 0.0.0.0'

# Build the SvelteKit frontend (static SPA) into frontend/build/
frontend-build:
    @just dev-run ./gradlew buildFrontend

# Start the web app in JVM mode (background); http://127.0.0.1:8080/
web: shadow
    docker compose --profile web up -d web
    @echo "→ http://127.0.0.1:8080/"

# Stop any running web container
web-stop:
    -docker compose --profile web down web --remove-orphans

# Generate a 4-page sample PDF at build/test-data/sample.pdf
sample-pdf:
    @just dev-run ./gradlew createSamplePdf

# Auto-fix typos across the repo
typos-fix:
    @just dev-run typos --write-changes

# Report typos without auto-fixing
typos:
    @just dev-run typos

# Remove build outputs
clean:
    @just dev-run ./gradlew clean

# Report outdated deps (Gradle deps/plugins + Dockerfile/spotless/jacoco/security pins)
outdated:
    @just dev-run ./gradlew --console=plain --no-parallel --no-configuration-cache --warning-mode=none dependencyUpdates

# Pre-pull base image + build dev image + warm Gradle cache. Useful first-run / onboarding.
warmup:
    docker compose build dev
    @just dev-run ./gradlew --quiet help
    @echo "→ dev image built, Gradle cache primed."

# Build the app-image and run a Linux CLI smoke against build/test-data/sample.pdf.
# The grep on the output verifies the file exists, is readable, and contains
# the canonical "%PDF" header — enough to catch jpackage-bundle breakage.
smoke: sample-pdf package
    @just dev-run ./build/dist-jpackage/tate-yoko-pdf/bin/tate-yoko-pdf build/test-data/sample.pdf -o build/test-data/jpackage-out.pdf
    @just dev-run grep -q %PDF build/test-data/jpackage-out.pdf
    @echo "✓ jpackage CLI smoke passed"

# Remove this project's Docker artifacts (containers, networks, named volumes, locally-built images).
docker-clean:
    docker compose --profile dev --profile web down -v --remove-orphans --rmi local

# Show Docker disk usage (machine-wide) and this project's container/volume state.
docker-status:
    docker system df
    @echo
    @echo '--- tate-yoko-pdf ---'
    -docker compose --profile dev --profile web ps -a
    docker volume ls --filter 'label=com.docker.compose.project=tate-yoko-pdf'

# Launch an interactive TUI (lazydocker) to inspect machine-wide Docker state.
docker-tui:
    lazydocker
