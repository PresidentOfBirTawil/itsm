#!/usr/bin/env bash
# Double-click this file (Mac) or run it (Linux) to start Quell from the source code.
# It finds Java 25 or downloads a private copy into this folder (nothing is installed
# on the computer), builds Quell the first time, and opens your browser.
# Keep the window open while you use Quell; close it (or press Ctrl+C) to stop Quell.
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

echo
echo "  Quell"
echo "  -----"

java_major() { "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1; }

RUNTIME="$ROOT/.runtime"
JAVA=""
for candidate in "$RUNTIME"/*/Contents/Home/bin/java "$RUNTIME"/*/bin/java; do
    [ -x "$candidate" ] && JAVA="$candidate" && break
done
if [ -z "$JAVA" ]; then
    for candidate in "${JAVA_HOME:-/nonexistent}/bin/java" "$(command -v java || true)"; do
        if [ -n "$candidate" ] && [ -x "$candidate" ] && [ "$(java_major "$candidate" || echo 0)" -ge 25 ] 2>/dev/null; then
            JAVA="$candidate"
            break
        fi
    done
fi
if [ -z "$JAVA" ]; then
    echo "  Java 25 is not on this computer. Downloading a private copy into this folder (once, about 200 MB)..."
    case "$(uname -s)" in Darwin) OS=mac ;; *) OS=linux ;; esac
    case "$(uname -m)" in arm64 | aarch64) ARCH=aarch64 ;; *) ARCH=x64 ;; esac
    mkdir -p "$RUNTIME"
    curl -fL --progress-bar -o "$RUNTIME/jdk.tar.gz" \
        "https://api.adoptium.net/v3/binary/latest/25/ga/$OS/$ARCH/jdk/hotspot/normal/eclipse"
    tar -xzf "$RUNTIME/jdk.tar.gz" -C "$RUNTIME"
    rm "$RUNTIME/jdk.tar.gz"
    for candidate in "$RUNTIME"/*/Contents/Home/bin/java "$RUNTIME"/*/bin/java; do
        [ -x "$candidate" ] && JAVA="$candidate" && break
    done
fi
export JAVA_HOME="$(cd "$(dirname "$JAVA")/.." && pwd)"
echo "  Using Java from $JAVA_HOME"

JAR="$ROOT/target/quell.jar"
if [ ! -f "$JAR" ] || [ -n "$(find "$ROOT/src" "$ROOT/pom.xml" -newer "$JAR" -type f -print -quit)" ]; then
    echo "  Building Quell. The first time this downloads its parts and takes a few minutes..."
    ./mvnw -q -B -DskipTests package
fi

echo "  Starting... your browser opens in a few seconds."
echo "  Keep this window open while you use Quell. Close it to stop Quell."
echo
exec "$JAVA" -Dapp.desktop=true -Dapp.data-dir="$ROOT/data" -jar "$JAR"
