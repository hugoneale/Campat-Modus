#!/usr/bin/env python3
"""Minimal Minecraft launcher for automated client tests (offline mode).

Resolves a launcher profile (e.g. the Forge one written by the Forge installer, which inherits the
vanilla 1.21.1 profile), downloads missing libraries, natives and assets, and runs the game.

usage: launch_client.py <installDir> <versionId> <gameDir> [extra game args...]
Environment: JAVA (default "java"), XMX (default 3G).
"""
import hashlib, json, os, platform, subprocess, sys, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES = "https://resources.download.minecraft.net/"


def fetch(url, dest, sha1=None):
    if os.path.exists(dest) and (sha1 is None or sha1file(dest) == sha1):
        return dest
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=120) as r, open(tmp, "wb") as f:
                f.write(r.read())
            break
        except Exception:
            if attempt == 3:
                raise
    os.replace(tmp, dest)
    return dest


def sha1file(p):
    h = hashlib.sha1()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def rules_allow(rules):
    if not rules:
        return True
    allowed = False
    for r in rules:
        ok = True
        if "os" in r:
            o = r["os"]
            if "name" in o and o["name"] != "linux":
                ok = False
            if "arch" in o and o["arch"] != "x86":
                ok = ok and False if platform.machine() not in ("i386", "i686") else ok
        if "features" in r:
            ok = False
        if ok:
            allowed = r["action"] == "allow"
    return allowed


def load_profile(install, vid):
    path = os.path.join(install, "versions", vid, vid + ".json")
    if not os.path.exists(path):
        manifest = json.loads(urllib.request.urlopen(MANIFEST).read())
        v = next(v for v in manifest["versions"] if v["id"] == vid)
        fetch(v["url"], path)
    prof = json.load(open(path))
    if "inheritsFrom" in prof:
        parent = load_profile(install, prof["inheritsFrom"])
        merged = dict(parent)
        merged["libraries"] = prof.get("libraries", []) + parent.get("libraries", [])
        merged["mainClass"] = prof["mainClass"]
        args = {"game": parent["arguments"]["game"] + prof.get("arguments", {}).get("game", []),
                "jvm": parent["arguments"]["jvm"] + prof.get("arguments", {}).get("jvm", [])}
        merged["arguments"] = args
        merged["id"] = prof["id"]
        merged["_jarVersion"] = parent.get("_jarVersion", parent["id"])
        return merged
    prof["_jarVersion"] = prof["id"]
    return prof


def main():
    install, vid, game_dir = os.path.abspath(sys.argv[1]), sys.argv[2], os.path.abspath(sys.argv[3])
    extra = sys.argv[4:]
    prof = load_profile(install, vid)
    libdir = os.path.join(install, "libraries")
    natives = os.path.join(install, "natives", vid)
    os.makedirs(natives, exist_ok=True)

    cp, seen, jobs = [], set(), []
    for lib in prof["libraries"]:
        if not rules_allow(lib.get("rules")):
            continue
        art = lib.get("downloads", {}).get("artifact")
        name = lib["name"]
        key = ":".join(name.split(":")[:2] + name.split(":")[3:])
        if art:
            dest = os.path.join(libdir, art["path"])
            if art.get("url"):
                jobs.append((art["url"], dest, art.get("sha1")))
            if key not in seen:
                seen.add(key)
                cp.append(dest)
        for nat in (lib.get("downloads", {}).get("classifiers") or {}).values():
            pass
    with ThreadPoolExecutor(16) as ex:
        list(ex.map(lambda j: fetch(*j), jobs))
    # LWJGL 3 natives ship as separate "natives-linux" artifacts on the classpath; extract .so files too
    for p in cp:
        if "natives-linux" in p and os.path.exists(p):
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    if n.endswith(".so"):
                        target = os.path.join(natives, os.path.basename(n))
                        if not os.path.exists(target):
                            with open(target, "wb") as f:
                                f.write(z.read(n))

    jar_ver = prof["_jarVersion"]
    client_jar = os.path.join(install, "versions", jar_ver, jar_ver + ".jar")
    fetch(prof["downloads"]["client"]["url"], client_jar, prof["downloads"]["client"]["sha1"])
    cp.append(client_jar)

    # assets
    ai = prof["assetIndex"]
    idx_path = os.path.join(install, "assets", "indexes", ai["id"] + ".json")
    fetch(ai["url"], idx_path, ai["sha1"])
    objs = json.load(open(idx_path))["objects"]
    jobs = []
    for o in objs.values():
        h = o["hash"]
        jobs.append((RESOURCES + h[:2] + "/" + h, os.path.join(install, "assets", "objects", h[:2], h), None))
    with ThreadPoolExecutor(32) as ex:
        list(ex.map(lambda j: fetch(*j), jobs))

    vars_ = {
        "natives_directory": natives, "launcher_name": "campat-test", "launcher_version": "1",
        "classpath": os.pathsep.join(cp), "classpath_separator": os.pathsep, "library_directory": libdir,
        "version_name": vid, "auth_player_name": "CampatTester", "game_directory": game_dir,
        "assets_root": os.path.join(install, "assets"), "assets_index_name": ai["id"],
        "auth_uuid": "00000000-0000-0000-0000-000000000001", "auth_access_token": "0", "clientid": "0",
        "auth_xuid": "0", "user_type": "legacy", "version_type": "release", "user_properties": "{}",
    }

    def expand(args):
        out = []
        for a in args:
            if isinstance(a, dict):
                if not rules_allow(a.get("rules")):
                    continue
                vals = a["value"] if isinstance(a["value"], list) else [a["value"]]
            else:
                vals = [a]
            for v in vals:
                for k, val in vars_.items():
                    v = v.replace("${" + k + "}", val)
                out.append(v)
        return out

    jvm = ["-Xmx" + os.environ.get("XMX", "3G")] + os.environ.get("JVM_EXTRA", "").split() + expand(prof["arguments"]["jvm"])
    game = expand(prof["arguments"]["game"]) + extra
    os.makedirs(game_dir, exist_ok=True)
    cmd = [os.environ.get("JAVA", "java")] + jvm + [prof["mainClass"]] + game
    with open(os.path.join(game_dir, "launch-cmd.txt"), "w") as f:
        f.write(" ".join(cmd))
    sys.exit(subprocess.call(cmd, cwd=game_dir))


main()
