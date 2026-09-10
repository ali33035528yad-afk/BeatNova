#!/bin/sh
set -e
if [ -n "$CM_BUILD_DIR" ] && [ -x "$CM_BUILD_DIR/gradle-8.9/bin/gradle" ]; then exec "$CM_BUILD_DIR/gradle-8.9/bin/gradle" "$@"; fi
if command -v gradle >/dev/null 2>&1; then exec "$(command -v gradle)" "$@"; fi
if [ -x "/Users/builder/gradle-8.9/bin/gradle" ]; then exec "/Users/builder/gradle-8.9/bin/gradle" "$@"; fi
echo "Gradle 8.9 not found" >&2
exit 1
