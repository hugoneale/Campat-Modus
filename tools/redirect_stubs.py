#!/usr/bin/env python3
"""From build/audit/audit-missing.txt, list unresolved members on the given Forge owners with their
NeoForge descriptors, as members.txt R/S lines and Java helper signatures to fill in.
usage: redirect_stubs.py <HelperInternalName> <forgeOwnerPrefix>...
"""
import re, sys
ROOT = __import__("os").path.dirname(__import__("os").path.dirname(__import__("os").path.abspath(__file__)))
helper = sys.argv[1]
owners = sys.argv[2:]
rev = {}
for l in open(ROOT + "/loader/src/main/resources/META-INF/campat/mappings/classes.txt"):
    p = l.split()
    if len(p) == 3 and p[0] == "C":
        rev.setdefault(p[2], p[1])
def neo(desc):
    return re.sub(r'L([^;]+);', lambda m: "L" + rev.get(m.group(1), m.group(1)) + ";", desc)
def jtype(t):
    arr = 0
    while t.startswith("["): arr += 1; t = t[1:]
    prim = {"I": "int", "Z": "boolean", "J": "long", "D": "double", "F": "float", "V": "void", "B": "byte", "S": "short", "C": "char"}
    s = prim.get(t, t[1:-1].replace("/", ".").replace("$", ".") if t.startswith("L") else t)
    return s + "[]" * arr
def split(desc):
    args, i = [], 1
    while desc[i] != ")":
        j = i
        while desc[j] == "[": j += 1
        if desc[j] == "L": j = desc.index(";", j)
        args.append(desc[i:j + 1]); i = j + 1
    return args, desc[i + 1:]
for line in open(ROOT + "/build/audit/audit-missing.txt"):
    n, ref = line.split("\t")[:2]
    m = re.match(r'(method|field) (\S+?)\.([\w<>$]+)([(:].*)$', ref)
    if not m: continue
    kind, owner, name, desc = m.groups()
    if not any(owner.startswith(o) for o in owners): continue
    nowner = rev.get(owner, owner)
    hname = (name if name != "<init>" else "create" + owner.split("/")[-1].split("$")[-1])
    if kind == "method":
        args, ret = split(desc)
        params = ([] if name == "<init>" else [jtype("L" + owner + ";") + " self"]) + [jtype(a) + " a" + str(i) for i, a in enumerate(args)]
        r = jtype("L" + owner + ";") if name == "<init>" else jtype(ret)
        print(f"R {nowner} {name} {neo(desc)} {helper} {hname}   # {n} mods")
        print(f"    // public static {r} {hname}({', '.join(params)})")
    else:
        print(f"S {nowner} {name} {neo(desc[1:])} {helper} {name}   # {n} mods (field)")
