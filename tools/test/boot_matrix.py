#!/usr/bin/env python3
"""Boots each NeoForge mod of a corpus (with its required dependencies from the corpus) on a Forge
dedicated server running Campat Modus, and classifies the outcome.

usage: boot_matrix.py <corpusDir> <outDir> [--jobs N] [--only substr,...]
"""
import argparse, concurrent.futures as cf, io, json, os, re, subprocess, sys, tomllib, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "build/envs/forge-server"
CAMPAT = ROOT / "build/libs/campat-modus-0.1.0.jar"
BUILTIN = {"minecraft", "neoforge", "forge", "java", "fml", "javafml"}


def mods_in(data: bytes):
    """mod ids declared by a jar and its nested NeoForge jars, plus required deps (side aware)"""
    ids, deps, client_only = set(), set(), False
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names = set(z.namelist())
        if "META-INF/neoforge.mods.toml" in names:
            try:
                t = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace"))
            except Exception:
                t = {}
            for m in t.get("mods", []):
                ids.add(m.get("modId"))
            for owner, lst in (t.get("dependencies") or {}).items():
                for d in lst if isinstance(lst, list) else []:
                    typ = str(d.get("type", "required" if d.get("mandatory", True) else "optional")).lower()
                    if typ == "required" and str(d.get("side", "BOTH")).upper() in ("BOTH", "SERVER"):
                        deps.add(d.get("modId"))
            client_only = bool(t.get("clientSideOnly")) or all(str(m.get("displayTest", "")).upper() == "IGNORE_ALL_VERSION" for m in t.get("mods", [])) and False
        for n in names:
            if n.startswith("META-INF/") and n.endswith(".jar"):
                i, d, _ = mods_in(z.read(n))
                ids |= i
                deps |= d
    return ids, deps, client_only


def classify(log: str):
    if "Done (" in log:
        return "OK", ""
    m = re.search(r"Missing or unsupported mandatory dependencies:(.*?)(?:\n\S|\Z)", log, re.S)
    if m:
        return "MISSING_DEP", " ".join(m.group(1).split())[:300]
    for pat in [r"Caused by: ([^\n]+)", r"(Mixin apply failed[^\n]+)", r"(Exception[^\n]*)"]:
        found = re.findall(pat, log)
        if found:
            return "CRASH", found[-1][:400]
    return "UNKNOWN", log.strip().splitlines()[-1][:300] if log.strip() else "empty log"


PORTS = iter(range(25600, 26600))


def boot(name, jars, out: Path):
    port = next(PORTS)
    inst = out / "inst" / re.sub(r"[^A-Za-z0-9._-]", "_", name)
    r = subprocess.run(["bash", str(ROOT / "tools/test/run-server.sh"), str(SERVER), str(inst), str(CAMPAT), *map(str, jars)],
                       capture_output=True, text=True, env={**os.environ, "TIMEOUT": os.environ.get("TIMEOUT", "300"), "PORT": str(port)})
    log = (inst / "run.log").read_text(errors="replace") if (inst / "run.log").exists() else r.stdout + r.stderr
    status, detail = classify(log)
    # keep space: drop the world and libraries link, keep logs
    subprocess.run(["rm", "-rf", str(inst / "world"), str(inst / "mods"), str(inst / ".campat/cache")])
    return {"mod": name, "jars": [j.name for j in jars], "status": status, "detail": detail}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("corpus"); ap.add_argument("out")
    ap.add_argument("--jobs", type=int, default=2); ap.add_argument("--only", default="")
    a = ap.parse_args()
    corpus, out = Path(a.corpus), Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    info = {}
    for j in sorted(corpus.glob("*.jar")):
        try:
            info[j] = mods_in(j.read_bytes())
        except Exception as e:
            print("skip", j.name, e, file=sys.stderr)
    provider = {}
    for j, (ids, _, _) in info.items():
        for i in ids:
            provider.setdefault(i, j)
    targets = [j for j, (ids, _, _) in info.items() if ids]
    if a.only:
        keys = a.only.split(",")
        targets = [j for j in targets if any(k in j.name for k in keys)]
    plans = []
    for j in targets:
        closure, todo, missing = {j}, list(info[j][1]), set()
        while todo:
            d = todo.pop()
            if d in BUILTIN or d is None or d in info[j][0]:
                continue
            p = provider.get(d)
            if p is None:
                missing.add(d)
            elif p not in closure:
                closure.add(p)
                todo.extend(info[p][1])
        plans.append((j.name, sorted(closure), missing))
    results_file = out / "results.jsonl"
    done = set()
    if results_file.exists():
        done = {json.loads(l)["mod"] for l in results_file.read_text().splitlines() if l.strip()}
    with cf.ThreadPoolExecutor(a.jobs) as ex, results_file.open("a") as rf:
        futs = {}
        for name, jars, missing in plans:
            if name in done:
                continue
            if missing:
                rec = {"mod": name, "jars": [], "status": "DEP_NOT_IN_CORPUS", "detail": " ".join(sorted(missing))}
                rf.write(json.dumps(rec) + "\n"); rf.flush(); print(rec["status"], name, rec["detail"], flush=True)
                continue
            futs[ex.submit(boot, name, jars, out)] = name
        for f in cf.as_completed(futs):
            rec = f.result()
            rf.write(json.dumps(rec) + "\n"); rf.flush()
            print(rec["status"], rec["mod"], rec["detail"][:200], flush=True)


if __name__ == "__main__":
    main()
