#!/bin/sh
# Gradle wrapper script (POSIX sh)
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
exec "$SCRIPT_DIR/gradle/wrapper/gradlew" "$@" 2>/dev/null || \
  gradle -p "$SCRIPT_DIR" "$@"
