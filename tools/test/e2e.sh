#!/usr/bin/env bash
# End-to-end test: a Forge 1.21.1 dedicated server and a headless Forge client (Xvfb + Mesa) both
# running Campat Modus, the NeoForge test mod and any extra mods given. The client joins, plays for
# a while and quits; the server stops when the player leaves. Prints PASS/FAIL markers of both.
#
# usage: tools/test/e2e.sh <workDir> [extra mod jars...]
# needs: ./gradlew campatJar :testmods:neoforge:jar :testmods:autotest:jar and :tools:setupEnvs
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mkdir -p "$1" && cd "$1" && pwd)"; shift
ENVS="$ROOT/build/envs"
SECONDS_IN_WORLD="${SECONDS_IN_WORLD:-25}"
MODS=("$ROOT/build/libs/campat-modus-0.1.0.jar" "$ROOT/testmods/autotest/build/libs/campat-autotest-1.0.0.jar"
      "$ROOT/testmods/neoforge/build/libs/campat-neoforge-testmod-1.0.0.jar" "$@")

SRV="$WORK/server"; CLI="$WORK/client"
mkdir -p "$SRV/mods" "$CLI/mods"
find "$SRV/mods" "$CLI/mods" -maxdepth 1 -name '*.jar' -delete
for m in "${MODS[@]}"; do cp "$m" "$SRV/mods/"; cp "$m" "$CLI/mods/"; done
rm -f "$SRV/logs/latest.log" "$CLI/logs/latest.log" 2>/dev/null
rm -rf "$SRV/world"

# server
ln -sfn "$ENVS/forge-server/libraries" "$SRV/libraries"
cp "$ENVS/forge-server/run.sh" "$ENVS"/forge-server/*-shim.jar "$SRV/"
echo eula=true > "$SRV/eula.txt"
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=-1\nenforce-secure-profile=false\n' > "$SRV/server.properties"
printf -- '-Xmx3G\n-Dcampat.autotest.stopAfterLeave=true\n' > "$SRV/user_jvm_args.txt"
(cd "$SRV" && timeout 900 bash run.sh nogui < /dev/null > "$SRV/run.log" 2>&1) &
SPID=$!
for i in $(seq 1 300); do
  grep -q 'Done (' "$SRV/run.log" 2>/dev/null && break
  kill -0 $SPID 2>/dev/null || break
  sleep 1
done
if ! grep -q 'Done (' "$SRV/run.log"; then echo "SERVER FAILED TO START"; tail -40 "$SRV/run.log"; exit 1; fi
echo "server up"

# client
[ -f "$CLI/options.txt" ] || printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\nsoundCategory_master:0.0\n' > "$CLI/options.txt"
JVM_EXTRA="-Dcampat.autotest.join=localhost:25565 -Dcampat.autotest.seconds=$SECONDS_IN_WORLD" LIBGL_ALWAYS_SOFTWARE=1 \
  timeout 900 xvfb-run -a -s "-screen 0 1280x720x24" python3 "$ROOT/tools/test/launch_client.py" "$ENVS/forge" 1.21.1-forge-52.1.20 "$CLI" > "$CLI/run.log" 2>&1
echo "client exited: $?"
wait $SPID; echo "server exited: $?"

echo "== results =="
for side in server client; do
  log="$WORK/$side/run.log"
  grep -ho '\[CAMPATTEST\] \(PASS\|FAIL\) .*' "$log" | sed "s/^/$side /" | sort -u
  grep -q '\[AUTOTEST\] joined world' "$log" && echo "$side joined-world"
  grep -E 'Exception|Error:|FATAL' "$log" | grep -v 'SoundEngine\|OpenAL\|ALSA\|oshi\|OpenGL debug\|Failed to open\|audio' | head -5 | sed "s/^/$side ERR /"
done
