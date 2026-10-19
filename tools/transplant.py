#!/usr/bin/env python3
"""Copy NeoForge source files into the runtime, rewriting references to NeoForge classes that have a
Forge counterpart (per the generated class table) unless the runtime provides the NeoForge class itself.

usage: transplant.py <neoforge-sources-dir> <neo.class.Name>...   (run from the repository root)
Inner classes come along with their outer class. Existing files are not overwritten.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUNTIME = os.path.join(ROOT, "runtime/src/main/java")
CLASSES = os.path.join(ROOT, "loader/src/main/resources/META-INF/campat/mappings/classes.txt")

def provided():
    out = set()
    for d, _, files in os.walk(os.path.join(RUNTIME, "net/neoforged")):
        for f in files:
            if f.endswith(".java"):
                out.add(os.path.relpath(os.path.join(d, f), RUNTIME)[:-5].replace(os.sep, "."))
    return out

def renames(keep):
    m = {}
    for line in open(CLASSES):
        p = line.split()
        if len(p) == 3 and p[0] == "C":
            neo, forge = p[1].replace("/", ".").replace("$", "."), p[2].replace("/", ".").replace("$", ".")
            outer = p[1].split("$")[0].replace("/", ".")
            if outer in keep: continue
            m[neo] = forge
    return m

def rewrite(text, m):
    # longest names first so inner classes win over their outers
    imported = []
    for neo in sorted(m, key=len, reverse=True):
        if re.search(r'import\s+' + re.escape(neo) + r';', text):
            imported.append(neo)
        text = re.sub(r'(?<![\w.])' + re.escape(neo) + r'(?![\w])', m[neo], text)
    # imported classes whose Forge counterpart has another simple name are also used by simple name
    for neo in imported:
        old, new = neo.rsplit(".", 1)[1], m[neo].rsplit(".", 1)[1]
        if "." in old or old == new:
            continue
        text = re.sub(r'(?<![\w.$])' + re.escape(old) + r'(?![\w$])', new, text)
    return text

def postprocess(text):
    # extension interfaces cast `this` to the patched vanilla type, which javac rejects for final
    # classes and enums when the cast is not provably possible; route it through Object
    text = re.sub(r'\((\w[\w.]*(?:<[^()]*>)?)\) this\b', r'(\1) (Object) this', text)
    # NeoForge cancellable events implement ICancellableEvent; Forge needs @Cancelable as well
    text = re.sub(r'(\n(\s*))((?:public |protected )?(?:static )?(?:abstract )?(?:final )?class \w+[^{;]*\bICancellableEvent\b[^{;]*\{)',
                  lambda mm: mm.group(1) + "@net.minecraftforge.eventbus.api.Cancelable" + mm.group(1) + mm.group(3), text)
    text = text.replace("ICancellableEvent.super.", "super.")
    return text


def main():
    if sys.argv[1] == "--fix":
        for f in sys.argv[2:]:
            text = open(f, encoding="utf-8").read()
            open(f, "w", encoding="utf-8").write(postprocess(text))
        return
    src = sys.argv[1]
    wanted = sys.argv[2:]
    keep = provided() | set(wanted)
    m = renames(keep)
    for cls in wanted:
        rel = cls.replace(".", "/") + ".java"
        dst = os.path.join(RUNTIME, rel)
        if os.path.exists(dst):
            print("exists, skipped:", rel); continue
        text = open(os.path.join(src, rel), encoding="utf-8").read()
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        text = rewrite(text, m)
        # same-package references are by simple name; import the Forge class for renamed siblings
        pkg = cls.rsplit(".", 1)[0]
        imports = []
        for neo, forge in m.items():
            if neo.rsplit(".", 1)[0] == pkg and "." not in neo[len(pkg) + 1:]:
                simple = neo.rsplit(".", 1)[1]
                if re.search(r'(?<![\w.])' + simple + r'\b', text) and forge.rsplit(".", 1)[1] == simple:
                    imports.append("import " + forge + ";")
        if imports:
            text = re.sub(r'(package [\w.]+;\n)', lambda mm: mm.group(1) + "\n" + "\n".join(sorted(imports)) + "\n", text, count=1)
        open(dst, "w", encoding="utf-8").write(postprocess(text))
        print("transplanted", rel)

main()
