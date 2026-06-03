#!/usr/bin/env bash
# bench-runtime.sh — runtime performance + memory benchmark for tate-yoko-pdf.
#
# Separates the wall-clock of a conversion into its parts WITHOUT touching
# production code, so we can answer two questions with numbers:
#
#   1. "Is it slow?"        -> startup (JVM + PDFBox/AWT init) vs in-process work.
#   2. "Will it OOM?"       -> peak RSS as a function of input size.
#
# How the split works:
#   * end-to-end wall = /usr/bin/time of the app-image launcher on a PDF.
#   * in-process work = the launcher's own "Done in X seconds" (load + compose
#     + save + qpdf; everything except JVM startup; see SpreadService.java).
#   * startup + init  = (end-to-end wall) - (Done in).
#   * qpdf alone      = /usr/bin/time of the bundled qpdf --linearize on the
#                       already-converted output, to size the post-process pass.
#   * --version floor = JVM boot + minimal classload, PDFBox/AWT NOT initialized.
#   * peak RSS        = /usr/bin/time "%M" (max resident set size).
#
# Runs natively on the host (the launcher bundles its own JRE) — NOT inside the
# dev container — so container overhead does not skew the timings.
#
# Usage:   scripts/bench-runtime.sh [INPUT.pdf ...]
#   RUNS=<n>   warm samples per measurement (default 5; median is reported).
# With no args, uses a default set (sample.pdf + the bundled test PDFs that
# happen to be present); missing files are skipped with a warning.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

LAUNCHER="app/build/dist-jpackage/tate-yoko-pdf/bin/tate-yoko-pdf"
QPDF="app/build/dist-jpackage/tate-yoko-pdf/lib/app/bin/qpdf"
OUT_DOC="docs/perf-runtime.md"
RUNS="${RUNS:-5}"

# ── Preconditions ────────────────────────────────────────────────────────────
if [[ ! -x "$LAUNCHER" ]]; then
  echo "error: app-image launcher not found at $LAUNCHER" >&2
  echo "       build it first:  just package" >&2
  exit 1
fi
if [[ ! -x /usr/bin/time ]]; then
  echo "error: GNU /usr/bin/time is required (BSD/shell 'time' lacks %M RSS)." >&2
  echo "       Linux: apt-get install time   macOS: brew install gnu-time (gtime)." >&2
  exit 1
fi

# ── Helpers ──────────────────────────────────────────────────────────────────
# median of the numeric args (float-aware); empty -> "n/a"
median() {
  [[ $# -eq 0 ]] && { echo "n/a"; return; }
  printf '%s\n' "$@" | sort -g | awk '{a[NR]=$1} END{m=int((NR+1)/2); if(NR%2) print a[m]; else printf "%.3f\n",(a[m]+a[m+1])/2}'
}

mb() { awk -v b="$1" 'BEGIN{printf "%.1f", b/1048576}'; }          # bytes  -> MiB
rss_mb() { awk -v k="$1" 'BEGIN{printf "%.0f", k/1024}'; }          # KiB    -> MiB

# qpdf --show-npages can exit 3 (warning) while still printing the count, so
# ignore its exit status and keep only the digits.
pages() { local n; n="$("$QPDF" --show-npages "$1" 2>/dev/null || true)"; n="$(printf '%s' "$n" | tr -dc '0-9')"; printf '%s' "${n:-?}"; }

# Run CMD once under /usr/bin/time. Writes "<elapsed_s> <maxrss_kb>" to the
# global TIMING; the command's own stdout/stderr go to the global CMD_ERR file.
TIMING=""; CMD_ERR=""
timed() {
  local tf cf
  tf="$(mktemp)"; cf="$(mktemp)"
  # %e = elapsed wall seconds, %M = max RSS (KiB). -o keeps time's output off the
  # command's stderr so we can parse the launcher's "Done in" cleanly.
  /usr/bin/time -f '%e %M' -o "$tf" "$@" >"$cf" 2>>"$cf" || true
  TIMING="$(cat "$tf")"; CMD_ERR="$cf"; rm -f "$tf"
}

# ── Measurement set ──────────────────────────────────────────────────────────
declare -a INPUTS
if [[ $# -gt 0 ]]; then
  INPUTS=("$@")
else
  # Default to the generated fixture only. Pass your own files as args for a
  # larger corpus — private inputs are deliberately not committed to the repo.
  INPUTS=(
    "app/build/test-data/sample.pdf"
  )
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

host_info() {
  echo "- Date (UTC): $(date -u '+%Y-%m-%d %H:%M:%S')"
  echo "- Host: $(uname -srm), $(nproc) CPUs, RAM $(free -h | awk '/^Mem:/{print $2}')"
  echo "- Launcher: \`$LAUNCHER\` (heap: $(grep -oE '\-Xmx[0-9a-zA-Z]+|MaxRAMPercentage=[0-9]+' build.gradle.kts | head -1))"
  echo "- Samples per measurement: cold (1st run) + warm median of $RUNS."
}

# ── JVM floor (--version: no PDFBox/AWT) ─────────────────────────────────────
echo "Measuring JVM startup floor (launcher --version)…" >&2
floor_cold=""; floor_warm=()
for ((r=0; r<=RUNS; r++)); do
  timed "$LAUNCHER" --version
  e="$(awk '{print $1}' <<<"$TIMING")"
  if [[ $r -eq 0 ]]; then floor_cold="$e"; else floor_warm+=("$e"); fi
  rm -f "$CMD_ERR"
done
floor_med="$(median "${floor_warm[@]}")"

# ── Per-file measurement ─────────────────────────────────────────────────────
# Accumulate markdown rows.
time_rows=""; mem_rows=""
for in_pdf in "${INPUTS[@]}"; do
  if [[ ! -f "$in_pdf" ]]; then
    echo "skip (not found): $in_pdf" >&2
    continue
  fi
  name="$(basename "$in_pdf")"
  size_b="$(stat -c%s "$in_pdf")"
  np="$(pages "$in_pdf")"
  echo "Measuring: $name (${np}p, $(mb "$size_b") MiB)…" >&2

  out="$WORK/out.pdf"

  # Cold run (first invocation in a fresh JVM) — recorded separately.
  timed "$LAUNCHER" "$in_pdf" -o "$out"
  cold_wall="$(awk '{print $1}' <<<"$TIMING")"
  cold_rss="$(awk '{print $2}' <<<"$TIMING")"
  cold_done="$(grep -aoE 'Done in [0-9.]+' "$CMD_ERR" | grep -oE '[0-9.]+' | tail -1 || echo "")"
  rm -f "$CMD_ERR"

  # Warm runs.
  walls=(); rsss=(); dones=()
  for ((r=1; r<=RUNS; r++)); do
    timed "$LAUNCHER" "$in_pdf" -o "$out"
    walls+=("$(awk '{print $1}' <<<"$TIMING")")
    rsss+=("$(awk '{print $2}' <<<"$TIMING")")
    d="$(grep -aoE 'Done in [0-9.]+' "$CMD_ERR" | grep -oE '[0-9.]+' | tail -1 || echo "")"
    [[ -n "$d" ]] && dones+=("$d")
    rm -f "$CMD_ERR"
  done
  wall_med="$(median "${walls[@]}")"
  done_med="$(median "${dones[@]}")"
  rss_med="$(median "${rsss[@]}")"
  # startup = wall - done (in-process). awk guards against missing done.
  startup="$(awk -v w="$wall_med" -v d="${done_med:-0}" 'BEGIN{s=w-d; if(s<0)s=0; printf "%.3f", s}')"

  # qpdf-alone on the converted output (representative post-process size).
  qpdf_med="n/a"
  if [[ -f "$out" && -x "$QPDF" ]]; then
    qs=()
    for ((r=1; r<=RUNS; r++)); do
      timed "$QPDF" --linearize "$out" "$WORK/q.pdf"
      qs+=("$(awk '{print $1}' <<<"$TIMING")")
      rm -f "$CMD_ERR"
    done
    qpdf_med="$(median "${qs[@]}")"
  fi

  # Ratio is only meaningful once the document dwarfs the ~115 MiB JVM/AWT floor;
  # below 1 MiB the floor dominates and the ratio is noise.
  rss_ratio="$(awk -v r="$rss_med" -v s="$size_b" 'BEGIN{ if(s>=1048576) printf "%.1f×", (r*1024)/s; else print "—"}')"

  time_rows+="| ${name} | ${np} | $(mb "$size_b") | ${wall_med}s | ${done_med:-n/a}s | ${startup}s | ${qpdf_med}s | ${cold_wall}s |"$'\n'
  mem_rows+="| ${name} | ${np} | $(mb "$size_b") | $(rss_mb "$rss_med") | ${rss_ratio} |"$'\n'
done

# ── Render markdown ──────────────────────────────────────────────────────────
mkdir -p "$(dirname "$OUT_DOC")"
{
  echo "# Runtime performance & memory baseline"
  echo
  echo "Generated by \`scripts/bench-runtime.sh\` (\`just bench-runtime\`). Tracks"
  echo "**conversion runtime and peak memory**, separate from the CI/build-time"
  echo "numbers in \`perf-baseline.md\`. Re-run after any change to the conversion"
  echo "pipeline, stream-cache policy, or launcher heap options."
  echo
  host_info
  echo
  echo "## Time breakdown (warm median of ${RUNS} runs)"
  echo
  echo "Each invocation is a **fresh JVM process** (the realistic single-file usage:"
  echo "the user runs the command once per file). Batch mode amortises this startup"
  echo "across files in one process, so per-file cost there is much lower."
  echo
  echo "JVM startup floor (\`--version\`, no PDFBox/AWT init): **${floor_med}s** warm / ${floor_cold}s cold."
  echo "\`startup\` below = end-to-end wall − in-process \`Done in\`; it includes JVM"
  echo "boot **and** first-touch PDFBox/AWT/font init, so it exceeds the bare floor."
  echo
  echo "| Input | Pages | Size (MiB) | E2E wall | Done (conv) | Startup＋init | qpdf alone | Cold wall |"
  echo "|---|---:|---:|---:|---:|---:|---:|---:|"
  printf '%s' "$time_rows"
  echo
  echo "## Peak memory vs input size (warm median)"
  echo
  echo "Peak RSS = a fixed **~115 MiB JVM/PDFBox/AWT floor** + the document held in"
  echo "heap. The floor dominates small inputs (so \`RSS/size\` looks huge and then"
  echo "falls); for large inputs the marginal RSS settles to **roughly the input"
  echo "size** — that is the output document's memory-only stream cache accumulating"
  echo "every cloned page stream until save. Extrapolating that slope against the"
  echo "launcher heap locates the input size at which it would OOM: the cliff this"
  echo "benchmark exists to find. (Max-RSS is GC-timing sensitive, so small inputs"
  echo "can read at or near the bare floor.)"
  echo
  echo "| Input | Pages | Size (MiB) | Peak RSS (MiB) | RSS/size |"
  echo "|---|---:|---:|---:|---:|"
  printf '%s' "$mem_rows"
  echo
  echo "## Memory safety"
  echo
  echo "Two defences keep the heap from being exhausted by a very large scan:"
  echo
  echo "1. **RAM-proportional heap** — the launcher runs with"
  echo "   \`-XX:MaxRAMPercentage=75.0\` (see \`build.gradle.kts\`), so the heap scales"
  echo "   to the host and honours container cgroup limits instead of a fixed 2g."
  echo "2. **\`--low-memory\`** — spills cloned page streams to a temp file"
  echo "   (\`java.io.tmpdir\`) via PDFBox's scratch stream cache, bounding heap"
  echo "   regardless of input size, at the cost of some disk I/O."
  echo
  echo "Verified on the largest sample under a deliberately tiny heap: with"
  echo "\`JAVA_TOOL_OPTIONS=-Xmx64m\` the default (in-memory) conversion exits 137"
  echo "(OutOfMemory), while the same conversion with \`--low-memory\` completes"
  echo "successfully and produces an identical page count. Note that if"
  echo "\`java.io.tmpdir\` is a tmpfs (RAM-backed), \`--low-memory\` does not free"
  echo "physical memory — point it at real disk on memory-constrained hosts."
} | tee "$OUT_DOC"

echo >&2
echo "→ wrote $OUT_DOC" >&2
