#!/usr/bin/env bash
# Forward a local port to the TV's ADB port through a host that can reach it.
#
# Needed when this Mac sits on a different network than the TV (for example the TV is on the
# router's IoT VLAN and the router refuses cross-zone traffic). Start it, then point the tools at
# 127.0.0.1:
#
#   scripts/adb-tunnel.sh
#   TV_HOST=127.0.0.1 tools/venv/bin/python -I tools/tv.py --probe
#
# Set TV_IP, ROUTER (an ~/.ssh/config host) and PORT to override the defaults.
set -euo pipefail

TV_IP="${TV_IP:-192.168.10.208}"
ROUTER="${ROUTER:-home-router}"
PORT="${PORT:-5555}"

if nc -z 127.0.0.1 "$PORT" 2>/dev/null; then
  echo "port $PORT already open locally; nothing to do"
  exit 0
fi

echo "tunnel: 127.0.0.1:$PORT -> $ROUTER -> $TV_IP:$PORT"
ssh -f -N -L "$PORT:$TV_IP:$PORT" \
  -o BatchMode=yes \
  -o ExitOnForwardFailure=yes \
  "$ROUTER"

sleep 1
if nc -z 127.0.0.1 "$PORT" 2>/dev/null; then
  echo "ready: TV_HOST=127.0.0.1 TV_PORT=$PORT"
else
  echo "tunnel failed; is $ROUTER reachable and the TV awake with USB debugging on?" >&2
  exit 1
fi
