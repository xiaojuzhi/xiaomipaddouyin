#!/bin/sh
#
# Gradle wrapper 启动脚本
#

# 解析实际目录
APP_HOME=$(cd "$(dirname "$0")" >/dev/null 2>&1 && pwd)

# 检查 java
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

# 执行 wrapper
exec "$JAVACMD" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
