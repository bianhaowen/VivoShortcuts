#!/bin/sh
GRADLE_HOME="/c/Users/bianh/devtools/gradle-8.12"
JAVA_HOME="/c/Users/bianh/devtools/jdk-17.0.19+10"

CLASSPATH=""
for jar in "$GRADLE_HOME/lib/"*.jar; do
    CLASSPATH="$CLASSPATH${CLASSPATH:+:}$jar"
done

export JAVA_HOME
exec "$JAVA_HOME/bin/java" -classpath "$CLASSPATH" org.gradle.launcher.GradleMain "$@"
