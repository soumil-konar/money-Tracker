#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

# Fallback to local JDK 21 if present and JAVA_HOME not set
if [ -z "$JAVA_HOME" ] && [ -d "/home/jarvis/.jdks/jdk-21.0.12.1+1" ]; then
    export JAVA_HOME="/home/jarvis/.jdks/jdk-21.0.12.1+1"
fi

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1; then
        echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
        exit 1
    fi
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=$(basename "$0")" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

