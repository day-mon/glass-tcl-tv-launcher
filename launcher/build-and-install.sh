#!/usr/bin/env bash
# Build the launcher and install it on the TV.
#
#   TV_HOST=127.0.0.1 ./build-and-install.sh       # after scripts/adb-tunnel.sh
#   TV_HOST=192.168.1.50 ./build-and-install.sh    # when the TV is on this network
#
# Set SET_HOME=1 to also make it the default Home app (reversible: point Home back at your old
# launcher with `cmd package set-home-activity --user 0 <old.package>`).
set -euo pipefail
cd "$(dirname "$0")"

: "${TV_HOST:?set TV_HOST to the TV address, e.g. TV_HOST=127.0.0.1 $0}"

PACKAGE="me.damon.glasshome"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home -v 17)}"
ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export JAVA_HOME ANDROID_HOME
export PATH="$JAVA_HOME/bin:$PATH"

[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleDebug -q --console=plain

PY=../tools/venv/bin/python
APK=app/build/outputs/apk/debug/app-debug.apk
REMOTE=/data/local/tmp/glasshome.apk

"$PY" -I ../tools/b64push.py "$APK" "$REMOTE" >/dev/null

{
  echo "pm install -r $REMOTE"
  echo "pm grant $PACKAGE android.permission.ACCESS_FINE_LOCATION"
  echo "rm -f $REMOTE"
  [ "${SET_HOME:-0}" = "1" ] && echo "cmd package set-home-activity --user 0 $PACKAGE"
  echo "am force-stop $PACKAGE"
  echo "am start -n $PACKAGE/.MainActivity"
} | "$PY" -I ../tools/tv.py --batch

echo
if [ "${SET_HOME:-0}" = "1" ]; then
  echo "Installed and set as Home."
else
  echo "Installed. It is running now; press Home to go back to your current launcher."
  echo "Make it the Home app with: SET_HOME=1 TV_HOST=$TV_HOST $0"
fi
