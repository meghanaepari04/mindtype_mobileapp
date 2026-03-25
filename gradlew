#!/bin/sh
dir="$(dirname "$0")"
exec java -Xmx2048m -classpath "$dir/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
