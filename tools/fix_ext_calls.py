#!/usr/bin/env python3
"""Adds casts to NeoForge extension interfaces where transplanted code calls methods that NeoForge
patches into vanilla classes (they are mixed in at runtime, so javac cannot see them on the vanilla
type). usage: fix_ext_calls.py <file.java>...   (run from the repository root)
"""
import os, re, sys

EXT = "runtime/src/main/java/net/neoforged/neoforge/common/extensions/"
# receiver expression pattern -> extension interface
RECEIVERS = [
    (r'self\(\)\.getBlock\(\)', "IBlockExtension"),
    (r'self\(\)\.getItem\(\)', "IItemExtension"),
    (r'stack\.getItem\(\)', "IItemExtension"),
    (r'self\(\)\.getType\(\)', "IFluidExtension"),
    (r'self\(\)\.getEffect\(\)\.value\(\)', "IMobEffectExtension"),
]

def methods_of(iface):
    path = EXT + iface + ".java"
    if not os.path.exists(path):
        return set()
    return set(re.findall(r'\b([a-z]\w*)\s*\(', open(path).read()))

def fix(text):
    for pat, iface in RECEIVERS:
        names = methods_of(iface)
        def repl(m):
            name = m.group(2)
            if name not in names:
                return m.group(0)
            return "((net.neoforged.neoforge.common.extensions." + iface + ") (Object) " + m.group(1) + ")." + name + "("
        text = re.sub(r'(' + pat + r')\.(\w+)\(', repl, text)
    return text

for f in sys.argv[1:]:
    t = open(f, encoding="utf-8").read()
    n = fix(t)
    if n != t:
        open(f, "w", encoding="utf-8").write(n)
        print("fixed", f)
