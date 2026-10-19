#!/usr/bin/env python3
"""Small source surgery helpers for adapting transplanted NeoForge code (importable module)."""
import re

R = "/home/user/Campat-Modus/runtime/src/main/java/"

def _read(f):
    return open(R + f, encoding="utf-8").read()

def _write(f, s):
    open(R + f, "w", encoding="utf-8").write(s)

def replace(f, old, new, count=1):
    s = _read(f)
    if old not in s:
        raise SystemExit(f"{f}: not found: {old[:100]}")
    _write(f, s.replace(old, new, count))

def sub(f, pattern, repl, count=0):
    s = _read(f)
    n, k = re.subn(pattern, repl, s, count=count)
    if k == 0:
        raise SystemExit(f"{f}: pattern not found: {pattern[:100]}")
    _write(f, n)

def _member_span(s, start):
    """from a declaration start, return end index after its body or ';'"""
    i = start
    depth = 0
    while i < len(s):
        c = s[i]
        if c == ';' and depth == 0:
            return i + 1
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return i + 1
        elif c == '"':
            i += 1
            while s[i] != '"':
                i += 2 if s[i] == '\\' else 1
        i += 1
    raise ValueError("unbalanced")

def delete_member(f, decl_regex, all=False):
    """Deletes the member whose declaration matches (plus its javadoc and annotations)."""
    s = _read(f)
    found = False
    while True:
        m = re.search(decl_regex, s)
        if not m:
            break
        found = True
        # back up to the start of the line, then over annotations / javadoc
        start = s.rfind("\n", 0, m.start()) + 1
        prev = s.rfind("\n", 0, start - 1) + 1
        while True:
            line = s[prev:start].strip()
            if line.startswith("@") or line.startswith("*") or line.startswith("/**") or line.endswith("*/"):
                start = prev
                prev = s.rfind("\n", 0, start - 1) + 1
            else:
                break
        end = _member_span(s, m.start())
        if end < len(s) and s[end] == "\n":
            end += 1
        s = s[:start] + s[end:]
        if not all:
            break
    if not found:
        raise SystemExit(f"{f}: member not found: {decl_regex}")
    _write(f, s)

def set_body(f, decl_regex, body):
    """Replaces the body of the method whose declaration matches."""
    s = _read(f)
    m = re.search(decl_regex, s)
    if not m:
        raise SystemExit(f"{f}: member not found: {decl_regex}")
    open_brace = s.index("{", m.end() - 1) if s[m.end() - 1] != "{" else m.end() - 1
    end = _member_span(s, m.start())
    _write(f, s[:open_brace] + "{\n        " + body + "\n    }" + s[end:])

def drop_import(f, cls):
    replace(f, "import " + cls + ";\n", "")
