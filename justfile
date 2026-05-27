# tate-yoko-pdf — task runner.
# Common verbs: `just check` / `just smoke` / `just web` / `just shell`.
# Groups are surfaced by `just --list`. lefthook bypasses high-level recipes
# and calls `just dev-run …` directly, so refactors here do not break hooks.

set shell := ["bash", "-cu"]
export DEV_UID := `id -u`
export DEV_GID := `id -g`

# Show available recipes grouped by section.
default:
    @just --list

# ─── Composition primitives ──────────────────────────────────────────────────
# Internal helpers other recipes call. They prefer the long-lived `dev-daemon`
# (started by `just dev-up`) and fall back to a one-shot container when it's
# not running. lefthook also calls `dev-run` directly — keep it public.

# Run an arbitrary command inside the dev container (lefthook entry point).
dev-run *args:
    @if [ -n "$(docker ps -q -f name=tate-yoko-pdf-dev-daemon)" ]; then \
        docker compose exec -T dev-daemon {{args}}; \
    else \
        docker compose run --rm dev {{args}}; \
    fi

# Run ./gradlew inside the dev container.
[private]
gradle *args:
    @just dev-run ./gradlew {{args}}



# Run pnpm (corepack-managed) inside the dev container, in frontend/.
# Run pnpm (corepack-managed) inside the dev container with cwd = /workspace/frontend.
# We can't compose via `dev-run bash -c '…'` because just's `*args` expansion
# drops shell quoting — passing `-w` to docker is the safe equivalent.
[private]
pnpm *args:
    @if [ -n "$(docker ps -q -f name=tate-yoko-pdf-dev-daemon)" ]; then \
        docker compose exec -T -w /workspace/frontend dev-daemon corepack pnpm {{args}}; \
    else \
        docker compose run --rm -w /workspace/frontend dev corepack pnpm {{args}}; \
    fi

# ─── Dev container ───────────────────────────────────────────────────────────

# Start the long-lived dev-daemon (keeps Gradle daemon + pnpm cache warm).
[group('dev')]
dev-up:
    docker compose --profile dev up -d dev-daemon
    @echo "→ dev-daemon up. Run 'just dev-down' to stop."

# Stop the long-lived dev-daemon.
[group('dev')]
dev-down:
    -docker compose --profile dev down dev-daemon --remove-orphans

# Run an arbitrary command in the dev container (requires `just dev-up`).
[group('dev')]
exec *cmd="bash":
    docker compose exec dev-daemon {{cmd}}

# Open an interactive shell in the dev container.
[group('dev')]
shell:
    @if [ -n "$(docker ps -q -f name=tate-yoko-pdf-dev-daemon)" ]; then \
        docker compose exec -it dev-daemon bash; \
    else \
        docker compose run --rm dev bash; \
    fi

# Pre-pull base image + build dev image + warm Gradle cache (first-run).
[group('dev')]
warmup:
    docker compose build dev
    @just gradle --quiet help
    @echo "→ dev image built, Gradle cache primed."

# ─── Quality ─────────────────────────────────────────────────────────────────

# Full check (backend: test + spotless + errorprone + nullaway + spotbugs + jacoco, then frontend lint).
[group('quality')]
check: && lint
    @just gradle check

# Backend tests only.
[group('quality')]
test:
    @just gradle test

# Frontend Biome lint + svelte-check.
[group('quality')]
lint:
    @just pnpm run biome:check
    @just pnpm run check

# Regenerate frontend/src/lib/types.ts from the sealed ProgressEvent.
[group('quality')]
generate-api-types:
    @just gradle generateApiTypes

# Auto-format Java (Spotless) + .ts/.js/.json (Biome) + .svelte (Prettier).
[group('quality')]
format:
    @just gradle spotlessApply
    @just pnpm run biome:format
    @just pnpm run format

# Auto-fix typos across the repo.
[group('quality')]
typos-fix:
    @just dev-run typos --write-changes

# Report typos without auto-fixing (pre-push gate).
[group('quality')]
typos:
    @just dev-run typos

# ─── Build & distribution ────────────────────────────────────────────────────

# Build the fat shadowJar at build/libs/tate-yoko-pdf-all.jar.
[group('build')]
shadow:
    @just gradle shadowJar

# Build the jpackage app-image (bundled JRE + shadow jar) under build/dist-jpackage/.
[group('build')]
package:
    @just gradle jpackageImage

# Build the app-image and convert a sample PDF through it (asserting `%PDF` magic on the output).
[group('build')]
smoke: sample-pdf package
    @just dev-run ./build/dist-jpackage/tate-yoko-pdf/bin/tate-yoko-pdf build/test-data/sample.pdf -o build/test-data/jpackage-out.pdf
    @just dev-run grep -q %PDF build/test-data/jpackage-out.pdf
    @echo "✓ jpackage CLI smoke passed"

# Generate a 4-page sample PDF at build/test-data/sample.pdf.
[group('build')]
sample-pdf:
    @just gradle createSamplePdf

# Remove Gradle build outputs.
[group('build')]
clean:
    @just gradle clean

# ─── Serve ───────────────────────────────────────────────────────────────────

# Start the web app in JVM mode (background) on http://127.0.0.1:8080/.
[group('serve')]
web: shadow
    docker compose --profile web up -d web
    @echo "→ http://127.0.0.1:8080/"

# Stop the web app.
[group('serve')]
web-stop:
    -docker compose --profile web down web --remove-orphans

# SvelteKit dev server (Vite HMR on :5173, /api & /ws proxied to :8080).
[group('serve')]
frontend-dev:
    @just pnpm run dev --host 0.0.0.0

# ─── Maintenance ─────────────────────────────────────────────────────────────

# Whole-project outdated report: Gradle deps/plugins + Dockerfile pins +
# security-patch floors + GitHub Actions (via checkExtraVersions) and frontend
# pnpm caret-cross drift. Pre-push gate (`-PfailOnUpdates=true`) counts the
# Gradle/Actions side toward the strict total; frontend drift is informational
# here and bumped on demand via `pnpm update --latest`.
[group('maint')]
outdated:
    @just gradle --console=plain --no-parallel --no-configuration-cache --warning-mode=none dependencyUpdates
    @echo
    @echo "=== Frontend (pnpm outdated) ==="
    @just pnpm outdated || true

# Remove this project's Docker artifacts (containers, networks, volumes, images). Confirmed prompt.
[group('maint'), confirm("Remove tate-yoko-pdf's containers / volumes / images? [y/N]")]
docker-clean:
    docker compose --profile dev --profile web down -v --remove-orphans --rmi local

# Show Docker disk usage (machine-wide) and this project's state.
[group('maint')]
docker-status:
    docker system df
    @echo
    @echo '--- tate-yoko-pdf ---'
    -docker compose --profile dev --profile web ps -a
    docker volume ls --filter 'label=com.docker.compose.project=tate-yoko-pdf'

# Launch lazydocker TUI for machine-wide Docker state.
[group('maint')]
docker-tui:
    lazydocker
