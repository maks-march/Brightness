#!/bin/sh
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
  else
    JAVA_EXE=java
  fi
  exec "$JAVA_EXE" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi

echo "gradle-wrapper.jar is missing. Open this project in Android Studio or install Gradle 8.9." >&2
exit 1
