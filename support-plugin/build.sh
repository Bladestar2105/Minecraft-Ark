#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
task_jdk="${JAVA_HOME:-$PWD/dino-plugin/.tools/jdk-25.0.5+8/Contents/Home}"
task_cp="${ARK_SUPPORT_BUILD_CLASSPATH:-$PWD/.deps/*:$PWD/accounts-plugin/build/ArkAccounts-1.0.2.jar}"
rm -rf support-plugin/build/classes support-plugin/build/test-classes
mkdir -p support-plugin/build/classes support-plugin/build/test-classes
"$task_jdk/bin/javac" --release 25 -sourcepath '' -cp "$task_cp" -d support-plugin/build/classes support-plugin/src/main/java/de/ark/support/*.java
"$task_jdk/bin/javac" --release 25 -sourcepath '' -cp "$task_cp:support-plugin/build/classes" -d support-plugin/build/test-classes support-plugin/src/test/java/de/ark/support/*.java
for task_test in SupportTest SupportRoutesTest SupportDeleteTest SupportNotificationsTest; do
  "$task_jdk/bin/java" -ea -cp "$task_cp:support-plugin/build/classes:support-plugin/build/test-classes" "de.ark.support.$task_test"
done
"$task_jdk/bin/jar" --create --date 2026-11-07T00:00:00Z --file support-plugin/build/ArkSupport-1.0.2.jar -C support-plugin/build/classes . -C support-plugin/src/main/resources .
shasum -a 256 support-plugin/build/ArkSupport-1.0.2.jar
