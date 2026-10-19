#!/usr/bin/env bash
# Installs the NeoForge and Forge clients that the mapping generator and the audit compare.
# usage: setup-envs.sh <outDir> <forgeVersion> <neoforgeVersion> <mcVersion>
set -euo pipefail
OUT="$1"; FORGE="$2"; NEO="$3"; MC="$4"
mkdir -p "$OUT/dl" "$OUT/forge/" "$OUT/neo/"
FI="$OUT/dl/forge-$MC-$FORGE-installer.jar"
NI="$OUT/dl/neoforge-$NEO-installer.jar"
[ -f "$FI" ] || curl -fsSL -o "$FI" "https://maven.minecraftforge.net/net/minecraftforge/forge/$MC-$FORGE/forge-$MC-$FORGE-installer.jar"
[ -f "$NI" ] || curl -fsSL -o "$NI" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
for d in forge neo; do echo '{"profiles":{}}' > "$OUT/$d/launcher_profiles.json"; done
FCLIENT="$OUT/forge/libraries/net/minecraftforge/forge/$MC-$FORGE/forge-$MC-$FORGE-client.jar"
NCLIENT="$OUT/neo/libraries/net/neoforged/neoforge/$NEO/neoforge-$NEO-client.jar"
[ -f "$FCLIENT" ] || (cd "$OUT/forge" && java -jar "$FI" --installClient . > install.log)
[ -f "$NCLIENT" ] || (cd "$OUT/neo" && java -jar "$NI" --installClient . > install.log)
# The server install downloads vanilla's libraries (DFU, brigadier, authlib, ...) which the client
# installers leave to the launcher. It is also what tools/test/run-server.sh boots.
mkdir -p "$OUT/forge-server"
[ -f "$OUT/forge-server/run.sh" ] || (cd "$OUT/forge-server" && java -jar "$FI" --installServer . > install.log)

# Environment lists: patched game jar first, then every library (first definition of a class wins).
NSRG=$(find "$OUT/neo/libraries/net/minecraft/client" -name "client-$MC-*-srg.jar" | head -1)
TOOLS='installertools|binarypatcher|jarsplitter|AutoRenamingTool|ForgeAutoRenamingTool|/client-'
{ echo "$FCLIENT"; find "$OUT/forge/libraries" -name '*.jar' | grep -Ev "$TOOLS" | grep -v "forge-$MC-$FORGE-client.jar";
  # vanilla libraries are downloaded by the launcher, not the installer; borrow NeoForge's copies
  find "$OUT/forge-server/libraries" -name '*.jar' | grep -Ev "$TOOLS|/net/minecraft/server/|/net/minecraftforge/forge/" || true; } > "$OUT/forge_env.txt"
{ echo "$NCLIENT"; echo "$NSRG"; find "$OUT/neo/libraries" -name '*.jar' | grep -Ev "$TOOLS" | grep -v "neoforge-$NEO-client.jar";
  find "$OUT/forge-server/libraries" -name '*.jar' | grep -Ev "$TOOLS|/net/minecraft/|/net/minecraftforge/|/cpw/|/org/spongepowered/" || true; } > "$OUT/neo_env.txt"
# NeoForge sources, used by tools/transplant.py and :tools:extGen
NS="$OUT/dl/neoforge-$NEO-sources.jar"
[ -f "$NS" ] || curl -fsSL -o "$NS" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-sources.jar"
[ -d "$OUT/neo-src/net" ] || (mkdir -p "$OUT/neo-src" && cd "$OUT/neo-src" && unzip -qo "$NS")
echo "Environments written to $OUT"
