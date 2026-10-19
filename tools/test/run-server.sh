#!/usr/bin/env bash
# Boots a Forge dedicated server with Campat Modus and the given mod jars, stops it once it is up.
# usage: run-server.sh <forgeServerInstallDir> <instanceDir> <campat.jar> [mod.jar...]
set -uo pipefail
INSTALL="$1"; INST="$2"; CAMPAT="$3"; shift 3
mkdir -p "$INST/mods"
find "$INST/mods" -maxdepth 1 -name '*.jar' -delete
ln -sfn "$INSTALL/libraries" "$INST/libraries"
cp "$INSTALL"/run.sh "$INSTALL"/*-shim.jar "$INST/" 2>/dev/null
echo eula=true > "$INST/eula.txt"
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=-1\n' > "$INST/server.properties"
echo '-Xmx3G' > "$INST/user_jvm_args.txt"
cp "$CAMPAT" "$INST/mods/"
for m in "$@"; do cp "$m" "$INST/mods/"; done
cd "$INST"
# feed "stop" once the server reports it is ready
( while ! grep -q 'Done (' logs/latest.log 2>/dev/null; do sleep 2; if ! kill -0 $$ 2>/dev/null; then exit; fi; done; echo stop ) | timeout "${TIMEOUT:-420}" bash run.sh nogui > run.log 2>&1
echo "exit=$?"
grep -q 'Done (' run.log && echo "SERVER STARTED" || echo "SERVER DID NOT START"
