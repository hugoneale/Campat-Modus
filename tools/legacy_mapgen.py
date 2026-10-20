#!/usr/bin/env python3
"""Generates the 1.20.1 (SRG) -> 1.21.1 (Mojang) translation table.

Forge 1.20.1 runs with Mojang class names and SRG member names (f_123_/m_123_). Forge 1.21.1 runs
with Mojang names throughout. Members are chained through Fabric's intermediary names, which stay
stable across Minecraft versions:

    SRG --MCPConfig--> obf 1.20.1 --intermediary 1.20.1--> intermediary --intermediary 1.21.1--> obf 1.21.1 --Mojang--> 1.21.1

usage: legacy_mapgen.py <dataDir> <out.txt> <report.txt> [rules.txt members.txt changed.tsv]

rules.txt holds hand written Mappings lines (C/F/M/R/S) whose owners, names and descriptors use
1.20.1 Mojang names; Minecraft member names are translated to SRG ids for members.txt.
dataDir holds joined.tsrg (MCPConfig 1.20.1), client_mappings-120.txt, client_mappings-121.txt,
int-1.20.1.tiny, int-1.21.1.tiny
"""
import re, sys
from pathlib import Path

PRIM = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J", "float": "F", "double": "D"}


def jtype(t, cmap):
    dims = t.count("[]")
    base = t.replace("[]", "")
    d = PRIM.get(base) or "L" + cmap.get(base.replace(".", "/"), base.replace(".", "/")) + ";"
    return "[" * dims + d


class Mojang:
    """obf <-> named, from a Mojang ProGuard mappings file"""

    def __init__(self, path):
        self.cls_n2o, self.cls_o2n = {}, {}
        raw = []
        cur = None
        for line in Path(path).read_text().splitlines():
            if line.startswith("#") or not line.strip():
                continue
            if not line.startswith(" "):
                n, o = line.rstrip(":").split(" -> ")
                cur = [n.replace(".", "/"), o.replace(".", "/"), []]
                raw.append(cur)
                self.cls_n2o[cur[0]] = cur[1]
                self.cls_o2n[cur[1]] = cur[0]
            else:
                cur[2].append(line.strip())
        self.fields = {}   # (obfOwner, obfName) -> named
        self.methods = {}  # (obfOwner, obfName, obfDesc) -> (named, namedDesc)
        self.named_methods = {}  # (namedOwner, named, namedDesc) -> obf key
        for n, o, members in raw:
            for m in members:
                left, obf = m.split(" -> ")
                left = re.sub(r"^\d+:\d+:", "", left)
                typ, rest = left.split(" ", 1)
                if "(" in rest:
                    name, args = rest[:-1].split("(")
                    args = [a for a in args.split(",") if a]
                    ndesc = "(" + "".join(jtype(a, {}) for a in args) + ")" + jtype(typ, {})
                    odesc = "(" + "".join(jtype(a, self.cls_n2o) for a in args) + ")" + jtype(typ, self.cls_n2o)
                    self.methods[(o, obf, odesc)] = (name, ndesc)
                    self.named_methods[(n, name, ndesc)] = (o, obf, odesc)
                else:
                    self.fields[(o, obf)] = rest


class Tiny:
    """intermediary v2: obf <-> intermediary"""

    def __init__(self, path):
        self.cls = {}      # obf -> int
        self.cls_rev = {}  # int -> obf
        self.f = {}        # (obfOwner, obf) -> int
        self.m = {}        # (obfOwner, obf, obfDesc) -> int
        self.f_rev, self.m_rev = {}, {}
        self.fdesc = {}
        cur = None
        for line in Path(path).read_text().splitlines()[1:]:
            p = line.split("\t")
            if p[0] == "c":
                cur = p[1]
                self.cls[p[1]] = p[2]
                self.cls_rev[p[2]] = p[1]
            elif len(p) > 1 and p[0] == "" and p[1] in ("f", "m"):
                kind, desc, obf, inter = p[1], p[2], p[3], p[4]
                if kind == "f":
                    self.f[(cur, obf)] = inter
                    self.fdesc[(cur, obf)] = desc
                    self.f_rev[inter] = (cur, obf, desc)
                else:
                    self.m[(cur, obf, desc)] = inter
                    self.m_rev.setdefault(inter, (cur, obf, desc))


def tsrg(path):
    """yields ('f', obfOwner, obf, None, srg) / ('m', obfOwner, obf, obfDesc, srg)"""
    cur = None
    for line in Path(path).read_text().splitlines()[1:]:
        if line.startswith("\t\t"):
            continue
        if not line.startswith("\t"):
            cur = line.split()[0]
            continue
        p = line.strip().split()
        if len(p) == 3:
            yield ("f", cur, p[0], None, p[1])
        elif len(p) == 4:
            yield ("m", cur, p[0], p[1], p[2])


def remap_desc(desc, f):
    return re.sub(r"L([^;]+);", lambda m: "L" + f(m.group(1)) + ";", desc)


def main():
    data, out, report = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    moj120, moj121 = Mojang(data / "client_mappings-120.txt"), Mojang(data / "client_mappings-121.txt")
    int120, int121 = Tiny(data / "int-1.20.1.tiny"), Tiny(data / "int-1.21.1.tiny")

    # classes: Mojang 1.20.1 name -> Mojang 1.21.1 name
    class_renames, removed_classes = {}, []
    for n120, o120 in moj120.cls_n2o.items():
        i = int120.cls.get(o120)
        o121 = int121.cls_rev.get(i) if i else None
        n121 = moj121.cls_o2n.get(o121) if o121 else None
        if n121 is None:
            n121 = n120 if n120 in moj121.cls_n2o else None
        if n121 is None:
            removed_classes.append(n120)
        elif n121 != n120:
            class_renames[n120] = n121

    def c120(obf):  # obf 1.20.1 -> Mojang 1.20.1
        return moj120.cls_o2n.get(obf, obf)

    SRG = re.compile(r"[fm]_\d+_")
    matched, fallback, conflicts, changed, tsv = {}, {}, set(), {}, {}
    by_named = {}  # (1.20.1 owner, name, desc) -> srg; fields: (owner, name, None)
    for kind, owner, obf, odesc, srg in tsrg(data / "joined.tsrg"):
        if not SRG.fullmatch(srg):
            continue
        if kind == "f":
            n120 = moj120.fields.get((owner, obf))
            i = int120.f.get((owner, obf))
            tgt = int121.f_rev.get(i) if i else None
            n121 = moj121.fields.get((tgt[0], tgt[1])) if tgt else None
            sig = f"{c120(owner)}.{n120}"
            by_named[(c120(owner), n120, None)] = srg
            if n121 and n120:
                od = int120.fdesc.get((owner, obf))
                fd120 = remap_desc(od, lambda c: class_renames.get(c120(c), c120(c))) if od else None
                fd121 = remap_desc(tgt[2], lambda c: moj121.cls_o2n.get(c, c))
                if fd120 and fd120 != fd121:
                    changed[srg] = f"{srg}\t{sig}:{fd120}\t-> {n121}:{fd121}"
                    tsv.setdefault(srg, (srg, c120(owner), n120, remap_desc(od, c120), moj121.cls_o2n.get(tgt[0], tgt[0]), n121, fd121))
        else:
            n120, ndesc120 = moj120.methods.get((owner, obf, odesc), (None, None))
            i = int120.m.get((owner, obf, odesc))
            tgt = int121.m_rev.get(i) if i else None
            n121, ndesc121 = moj121.methods.get(tgt, (None, None)) if tgt else (None, None)
            sig = f"{c120(owner)}.{n120}{ndesc120}"
            by_named[(c120(owner), n120, ndesc120)] = srg
            if n121 and ndesc120:
                # descriptors in 1.21.1 names: compare after renaming 1.20.1 classes
                d120 = remap_desc(ndesc120, lambda c: class_renames.get(c, c))
                if d120 != ndesc121:
                    changed[srg] = f"{srg}\t{sig}\t-> {n121}{ndesc121}"
                    tsv.setdefault(srg, (srg, c120(owner), n120, ndesc120, moj121.cls_o2n.get(tgt[0], tgt[0]), n121, ndesc121))
        if n121 is None:
            if n120:
                fallback.setdefault(srg, (n120, sig))
            continue
        prev = matched.get(srg)
        if prev and prev != n121:
            conflicts.add(srg)
        matched[srg] = n121
    # not matched by intermediary anywhere: keep the 1.20.1 Mojang name, it often still exists
    removed = [f"{s}\t{sig}" for s, (n, sig) in fallback.items() if s not in matched]
    names = dict(matched)
    for s, (n, _) in fallback.items():
        names.setdefault(s, n)
    changed = list(changed.values())
    with out.open("w") as w:
        w.write("# generated by tools/legacy_mapgen.py: Minecraft 1.20.1 (SRG members) -> 1.21.1 (Mojang)\n")
        for a, b in sorted(class_renames.items()):
            w.write(f"C {a} {b}\n")
        for s, n in sorted(names.items()):
            if s not in conflicts:
                w.write(f"N {s} {n}\n")
    with report.open("w") as w:
        w.write(f"# {len(class_renames)} class renames, {len(removed_classes)} classes gone, {len(names)} SRG names, "
                f"{len(conflicts)} conflicts, {len(removed)} members unmatched, {len(changed)} descriptors changed\n")
        w.write("\n## classes gone in 1.21.1\n" + "\n".join(sorted(removed_classes)))
        w.write("\n\n## members without a 1.21.1 counterpart (kept 1.20.1 name)\n" + "\n".join(sorted(removed)))
        w.write("\n\n## members whose descriptor changed\n" + "\n".join(sorted(changed)))
        w.write("\n\n## conflicts\n" + "\n".join(sorted(conflicts)) + "\n")
    print(open(report).readline().strip())
    if len(sys.argv) > 6:
        rules, members, changed_tsv = Path(sys.argv[4]), Path(sys.argv[5]), Path(sys.argv[6])
        with changed_tsv.open("w") as w:
            for row in sorted(tsv.values()):
                w.write("\t".join(x or "" for x in row) + "\n")
        translate_rules(rules, members, by_named)


def translate_rules(rules, members, by_named):
    out = ["# generated by tools/legacy_mapgen.py from tools/legacy/rules.txt -- edit that file instead"]
    for no, line in enumerate(rules.read_text().splitlines(), 1):
        body = line.split("#", 1)[0].strip()
        if not body:
            out.append(line)
            continue
        p = body.split()
        if p[0] == "V":
            srg = by_named.get((p[1], p[2], p[3]))
            if srg is None and p[1].startswith(("net/minecraft/", "com/mojang/blaze3d/vertex/")):
                sys.exit(f"rules.txt:{no}: no 1.20.1 member {p[1]}.{p[2]}{p[3]}")
            if srg:
                p[2] = srg
            out.append(" ".join(p))
            continue
        if p[0] in ("M", "R", "S"):
            key = (p[1], p[2], p[3] if p[0] != "S" else None)
            srg = by_named.get(key)
            if srg is None and p[0] == "S":
                srg = by_named.get((p[1], p[2], None))
            if srg is None and p[1].startswith(("net/minecraft/", "com/mojang/blaze3d/", "com/mojang/math/", "com/mojang/realmsclient/")) \
                    and not p[1].startswith("com/mojang/blaze3d/systems/") and not p[2].startswith("<"):  # RenderSystem is not obfuscated
                sys.exit(f"rules.txt:{no}: no 1.20.1 member {p[1]}.{p[2]}{p[3] if p[0] != 'S' else ''}")
            if srg and p[0] == "M":
                # SRG ids are renamed globally (N), which wins over owner keyed M renames
                out.append(f"N {srg} {p[4]}")
                continue
            if srg:
                p[2] = srg
        elif p[0] == "F":
            srg = by_named.get((p[1], p[2], None))
            # enum constants keep their names in SRG: plain owner keyed F line
            if srg is None and p[1].startswith(("net/minecraft/", "com/mojang/blaze3d/", "com/mojang/math/", "com/mojang/realmsclient/")) and not p[2].isupper():
                sys.exit(f"rules.txt:{no}: no 1.20.1 field {p[1]}.{p[2]}")
            if srg:
                # SRG names are global: an N line keyed on the id renames it everywhere
                out.append(f"N {srg} {p[3]}")
                continue
        out.append(" ".join(p))
    members.write_text("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
