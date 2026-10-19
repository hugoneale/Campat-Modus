#!/usr/bin/env python3
"""Downloads the most downloaded Modrinth mods for a game version and loader.
usage: fetch_corpus.py <outDir> <count> [gameVersion] [loader]"""
import json, sys, urllib.request, urllib.parse, os, time
out = sys.argv[1]; N = int(sys.argv[2]); VER = sys.argv[3] if len(sys.argv) > 3 else "1.21.1"; LOADER = sys.argv[4] if len(sys.argv) > 4 else "neoforge"
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "campat-modus-audit/0.1 (hugoneale5)"})
    for i in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as r: return r.read()
        except Exception as e:
            time.sleep(2**i); err = e
    raise err
facets = json.dumps([["versions:" + VER],["categories:" + LOADER],["project_type:mod"]])
hits = []
off = 0
while len(hits) < N*2:
    q = urllib.parse.urlencode({"facets": facets, "limit": 100, "offset": off, "index": "downloads"})
    h = json.loads(get("https://api.modrinth.com/v2/search?" + q))["hits"]
    if not h: break
    hits += h; off += 100
got = 0; manifest = []
for h in hits:
    if got >= N: break
    slug = h["slug"]
    q = urllib.parse.urlencode({"loaders": json.dumps([LOADER]), "game_versions": json.dumps([VER])})
    try: vers = json.loads(get(f"https://api.modrinth.com/v2/project/{slug}/version?" + q))
    except Exception as e: print("skip", slug, e); continue
    if not vers: continue
    v = vers[0]
    f = next((f for f in v["files"] if f["primary"]), v["files"][0])
    if not f["filename"].endswith(".jar"): continue
    dest = os.path.join(out, f["filename"])
    if not os.path.exists(dest):
        open(dest, "wb").write(get(f["url"]))
    manifest.append({"slug": slug, "file": f["filename"], "loaders": v["loaders"], "downloads": h["downloads"]})
    got += 1
    print(got, slug, v["loaders"], f["filename"], flush=True)
json.dump(manifest, open(os.path.join(out, "manifest.json"), "w"), indent=1)
