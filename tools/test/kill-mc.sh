#!/usr/bin/env bash
# Stops Minecraft processes left behind by test runs (never matches the calling shell).
for p in $(ps -eo pid=,args= | awk '/ForgeBootstrap|shim\.jar|unix_args\.txt|tools\/test\/e2e\.sh|Xvfb/ && !/awk/ {print $1}'); do
  [ "$p" != "$$" ] && [ "$p" != "$PPID" ] && kill "$p" 2>/dev/null
done
exit 0
