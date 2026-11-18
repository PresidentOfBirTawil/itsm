#!/usr/bin/env bash
#
# Builds the Quell desktop application for the operating system this runs on,
# with its own Java runtime inside, so the person installing it needs nothing else.
#
#   packaging/package.sh installer   Windows: a setup wizard (.exe), Mac: a disk image (.dmg),
#                                    Linux: a .deb package
#   packaging/package.sh portable    a folder with the application, zipped (no installation)
#
# Needs JDK 25 (with jpackage). On Windows the installer also needs the WiX Toolset.
# The finished files are in target/release.
set -euo pipefail

KIND="${1:-installer}"
cd "$(dirname "$0")/.."

VERSION="$(sed -n '/<artifactId>quell<\/artifactId>/{n;s/.*<version>\(.*\)<\/version>.*/\1/p;}' pom.xml)"
NAME="Quell"
# Everything in Java SE, plus the JDK parts the libraries use (crypto, locales, zip file systems, Unsafe).
MODULES="java.se,jdk.unsupported,jdk.unsupported.desktop,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.charsets,jdk.localedata,jdk.zipfs,jdk.management,jdk.net,jdk.naming.dns,jdk.httpserver,jdk.accessibility,jdk.security.auth,jdk.security.jgss"

case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) PLATFORM=windows ;;
    Darwin) PLATFORM=mac ;;
    *) PLATFORM=linux ;;
esac
echo "Building $NAME $VERSION ($KIND) for $PLATFORM"

if [ "${SKIP_BUILD:-}" != "true" ]; then
    ./mvnw -B -q -DskipTests package ${MAVEN_ARGS:-}
fi

# target/release is kept, so that "installer" and "portable" can be built one after the other.
rm -rf target/installer-input target/installer target/runtime
mkdir -p target/installer-input target/installer target/release
cp target/quell.jar target/installer-input/

# A trimmed Java runtime: only the modules above, without debugging data, documentation or tools.
jlink --add-modules "$MODULES" --include-locales en \
    --strip-debug --strip-native-commands --no-header-files --no-man-pages --compress zip-6 \
    --output target/runtime
find target/runtime -name "*.debuginfo" -delete

COMMON=(--name "$NAME" --app-version "$VERSION" --vendor "$NAME"
    --description "Intelligent IT service management"
    --copyright "Copyright 2026 $NAME"
    --input target/installer-input --main-jar quell.jar
    --java-options "-Dapp.desktop=true" --java-options "-Xmx768m" --java-options "-Dfile.encoding=UTF-8"
    --runtime-image target/runtime --dest target/installer)

case "$PLATFORM" in
    windows) ICON=(--icon packaging/icons/quell.ico) ;;
    mac) ICON=(--icon packaging/icons/quell.icns --mac-package-name "$NAME" --mac-package-identifier app.quell.desktop) ;;
    linux) ICON=(--icon packaging/icons/quell.png) ;;
esac

if [ "$KIND" = "portable" ]; then
    jpackage --type app-image "${COMMON[@]}" "${ICON[@]}"
    case "$PLATFORM" in
        windows) (cd target/installer && powershell -NoProfile -Command "Compress-Archive -Path '$NAME' -DestinationPath '../release/$NAME-Windows-Portable.zip'") ;;
        mac) (cd target/installer && ditto -c -k --keepParent "$NAME.app" "../release/$NAME-Mac-Portable.zip") ;;
        linux) tar -czf "target/release/$NAME-Linux-Portable.tar.gz" -C target/installer "$NAME" ;;
    esac
else
    case "$PLATFORM" in
        windows)
            # A setup wizard: per-user (no administrator rights needed), with a choice of folder,
            # a Start-menu entry and a desktop shortcut. The upgrade id lets new versions replace old ones.
            jpackage --type exe "${COMMON[@]}" "${ICON[@]}" \
                --win-per-user-install --win-dir-chooser --win-menu --win-menu-group "$NAME" \
                --win-shortcut --win-shortcut-prompt --win-upgrade-uuid 6f0c2a4e-3b7d-4f61-9c2e-8d5a1b7e4c90
            mv target/installer/*.exe "target/release/$NAME-Setup-Windows.exe"
            ;;
        mac)
            jpackage --type dmg "${COMMON[@]}" "${ICON[@]}"
            mv target/installer/*.dmg "target/release/$NAME-Mac.dmg"
            ;;
        linux)
            jpackage --type deb "${COMMON[@]}" "${ICON[@]}" \
                --linux-package-name quell --linux-shortcut --linux-menu-group Office --linux-app-category office
            mv target/installer/*.deb "target/release/$NAME-Linux.deb"
            ;;
    esac
fi
ls -la target/release
