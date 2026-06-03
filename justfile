# tate-yoko-pdf — task runner.
# Common verbs: `just check` / `just smoke` / `just shell`.
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

# ─── Dev container ───────────────────────────────────────────────────────────

# Start the long-lived dev-daemon (keeps the Gradle daemon + cache warm).
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

# Apply every auto-fix the toolchain can derive: typos + spotless Java format.
# OpenRewrite recipes are intentionally *not* applied here — those are
# semantic transforms, opt-in via `just rewrite`.
[group('quality')]
format:
    -@just dev-run typos --write-changes
    @just gradle spotlessApply

# Full quality gate. Auto-applies every fixable finding first via `format`, then
# verifies whatever Spotless / Error Prone / NullAway / SpotBugs / JaCoCo raise
# that can't be auto-fixed. CI runs `./gradlew check` directly, so any auto-fixes
# you forget to commit still get caught upstream.
[group('quality')]
check: format
    @just gradle check

# Strict verify-only (no auto-fix). Mirrors CI behaviour for the rare case you
# want to see what a clean-tree CI run would surface.
[group('quality')]
check-strict:
    @just gradle check

# Backend tests only.
[group('quality')]
test:
    @just gradle test

# Auto-fix typos across the repo.
[group('quality')]
typos-fix:
    @just dev-run typos --write-changes

# Report typos without auto-fixing (pre-push gate).
[group('quality')]
typos:
    @just dev-run typos

# Dry-run OpenRewrite — preview rewrite proposals without modifying sources.
# OpenRewrite reads Task.project at execution which is incompatible with the
# Gradle 9 configuration cache (same flag the ben-manes versions task needs).
[group('quality')]
rewrite-check:
    @just gradle rewriteDryRun --no-configuration-cache

# Apply OpenRewrite recipes in-place.
[group('quality')]
rewrite:
    @just gradle rewriteRun --no-configuration-cache

# Run Pitest mutation testing on domain + application packages.
# Reports kill rate per package; thresholds are 0 today (warning-only) so the
# run never breaks the build. Same configuration-cache constraint as
# ben-manes/openrewrite — Pitest reads Task.project at execution.
[group('quality')]
mutation:
    @just gradle pitest --no-configuration-cache

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

# Unlike most recipes this runs the launcher NATIVELY on the host (not via
# `dev-run`): the app-image bundles its own JRE, and going through the dev
# container would add overhead that skews the wall-clock and RSS numbers.
# Benchmark conversion runtime + peak memory; writes docs/perf-runtime.md.
[group('build')]
bench-runtime: sample-pdf package
    scripts/bench-runtime.sh

# Remove Gradle build outputs.
[group('build')]
clean:
    @just gradle clean

# ─── Maintenance ─────────────────────────────────────────────────────────────

# Whole-project outdated report: Gradle deps/plugins + Dockerfile pins +
# security-patch floors + GitHub Actions (via checkExtraVersions). Pre-push gate
# (`-PfailOnUpdates=true`) counts the Gradle/Actions side toward the strict total.
[group('maint')]
outdated:
    @just gradle --console=plain --no-parallel --no-configuration-cache --warning-mode=none dependencyUpdates

# Remove this project's Docker artifacts (containers, networks, volumes, images). Confirmed prompt.
[group('maint'), confirm("Remove tate-yoko-pdf's containers / volumes / images? [y/N]")]
docker-clean:
    docker compose --profile dev down -v --remove-orphans --rmi local

# Show Docker disk usage (machine-wide) and this project's state.
[group('maint')]
docker-status:
    docker system df
    @echo
    @echo '--- tate-yoko-pdf ---'
    -docker compose --profile dev ps -a
    docker volume ls --filter 'label=com.docker.compose.project=tate-yoko-pdf'

# Launch lazydocker TUI for machine-wide Docker state.
[group('maint')]
docker-tui:
    lazydocker
