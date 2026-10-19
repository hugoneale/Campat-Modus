#!/usr/bin/env python3
"""Condenses javac output (build/compile.log) into one line per error: file:line: message | symbol | source."""
import re, sys
lines = open(sys.argv[1] if len(sys.argv) > 1 else "build/compile.log").read().split("\n")
seen, out = set(), []
for i, l in enumerate(lines):
    m = re.match(r'^(/\S+\.java):(\d+): error: (.*)', l)
    if not m:
        continue
    f, n, msg = m.groups()
    sym = ""
    for j in range(i + 1, min(i + 6, len(lines))):
        if "symbol:" in lines[j]: sym = lines[j].strip()
        if "location:" in lines[j]: sym += " @ " + lines[j].strip().replace("location: ", "")
    src = lines[i + 1].strip() if i + 1 < len(lines) else ""
    if (f, n) not in seen:
        seen.add((f, n))
        out.append(f"{f.split('/java/')[-1]}:{n}: {msg} | {sym} | {src[:120]}")
print(len(out), "errors")
print("\n".join(out))
