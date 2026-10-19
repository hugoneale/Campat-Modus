#!/usr/bin/env python3
"""Adds a public no-arg constructor to runtime event classes that lack one.

Forge's event bus instantiates every listened-to event class through a public no-arg constructor
to build its listener list; NeoForge's bus does not, so transplanted NeoForge events lack one.
usage: event_noarg.py <java files...>
"""
import re, sys

DEFAULTS = {"boolean": "false", "int": "0", "long": "0L", "float": "0f", "double": "0d", "short": "(short) 0", "byte": "(byte) 0", "char": "'\\0'"}
CLASS = re.compile(r"(?:public |protected )?(?:static )?(?:abstract )?class (\w+)(?:<[^{]*?>)? extends [\w.<>?, ]*Event\b[^{]*\{")


def split_params(p):
    out, depth, cur = [], 0, ""
    for ch in p:
        if ch in "<(": depth += 1
        if ch in ">)": depth -= 1
        if ch == "," and depth == 0:
            out.append(cur); cur = ""
        else:
            cur += ch
    if cur.strip(): out.append(cur)
    return [x.strip() for x in out]


def default(param):
    t = re.sub(r"@\w+(\([^)]*\))?\s*", "", param).rsplit(" ", 1)[0].strip()
    t = t.replace("final ", "").strip()
    if t.endswith("..."):
        return "(" + t[:-3] + "[]) null"
    return DEFAULTS.get(t, "(" + re.sub(r"<.*>", "", t) + ") null")


def patch(src):
    out = src
    for m in list(CLASS.finditer(src)):
        name = m.group(1)
        if re.search(r"(public|protected)\s+" + name + r"\s*\(\s*\)", src):
            continue
        ctor = re.search(r"(public|protected)\s+" + name + r"\s*\(([^)]*)\)\s*\{", src[m.end():])
        if not ctor:
            continue
        args = ", ".join(default(p) for p in split_params(ctor.group(2)))
        body = "\n    /** campat: Forge's event bus needs a public no-arg constructor to build listener lists */\n    public " + name + "() {\n        this(" + args + ");\n    }\n"
        out = out.replace(m.group(0), m.group(0) + body, 1)
    return out


for f in sys.argv[1:]:
    s = open(f).read()
    p = patch(s)
    if p != s:
        open(f, "w").write(p)
        print("patched", f)
