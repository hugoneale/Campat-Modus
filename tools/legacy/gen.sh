#!/usr/bin/env bash
# Regenerates the legacy (1.20.1 -> 1.21.1) tables. Needs the mapping inputs in $1 (see legacy_mapgen.py).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/loader/src/main/resources/META-INF/campat/legacy"
mkdir -p "$OUT" "$ROOT/build/legacy"
python3 "$ROOT/tools/legacy_mapgen.py" "$1" "$OUT/srg.txt" "$ROOT/build/legacy/srg-report.txt" \
  "$ROOT/tools/legacy/rules.txt" "$OUT/members.txt" "$ROOT/build/legacy/changed.tsv"
