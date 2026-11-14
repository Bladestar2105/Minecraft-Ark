#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

task_java_home="${JAVA_HOME:-$PWD/.tools/jdk-25.0.5+8/Contents/Home}"
if [[ ! -x "$task_java_home/bin/javac" ]]; then
  echo 'Set JAVA_HOME to a JDK 25 installation.' >&2
  exit 1
fi
mkdir -p .deps build/classes build/test-classes

fetch() {
  local name="$1" sha="$2" url="$3"
  if [[ ! -f ".deps/$name" ]]; then
    curl --fail --silent --show-error --location --connect-timeout 10 --max-time 120 \
      --doh-url https://1.1.1.1/dns-query "$url" -o ".deps/$name"
  fi
  local actual
  actual="$(shasum -a 256 ".deps/$name")"
  if [[ "${actual%% *}" != "$sha" ]]; then
    echo "Checksum mismatch: .deps/$name; remove the bad download and retry." >&2
    exit 1
  fi
}

paper=https://repo.papermc.io/repository/maven-public
central=https://repo.maven.apache.org/maven2
fetch paper-api-26.3.build.236-stable.jar 018711f361a950cd8f07a132b71e6a170632a919c2666421084745aaa8eed167 "$paper/io/papermc/paper/paper-api/26.3.build.236-stable/paper-api-26.3.build.236-stable.jar"
fetch adventure-api-5.2.0.jar 7e52fe7190be3e87b3b3f71712cfa12315fcd27109f302a7b440c12f01fae827 "$central/net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar"
fetch adventure-key-5.2.0.jar 0184d173200e2eef8fbc791f622d1d58fd459f8930c616b5a4fe79e83eda6c55 "$central/net/kyori/adventure-key/5.2.0/adventure-key-5.2.0.jar"
fetch examination-api-1.3.0.jar c9237ffecb05428f6eff86216246ac70ce0b47b04c08ea7ca35020fde57f8492 "$central/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar"
fetch annotations-26.1.0.jar ebc7aec252ed0c7d2d04c039d7f00e69f7b86b1f493c741d67b3ef31b986b054 "$central/org/jetbrains/annotations/26.1.0/annotations-26.1.0.jar"
fetch jspecify-1.0.0.jar 1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab "$central/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"
fetch guava-33.6.0-jre.jar dc573e1fca4fd5454f4a5fd3d7da2df03002876a4175bafc14a95980dd7713b3 "$central/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar"
fetch joml-1.10.9.jar feca4db853371704338621c120acc5cc32300d8af635fa42414b6213301e216b "$central/org/joml/joml/1.10.9/joml-1.10.9.jar"
fetch bungeecord-chat.jar ce930b3ee048520a0ab538eb8d1627b301367fc1b300837235387aded6d89ba1 "$paper/net/md-5/bungeecord-chat/1.21-R0.2-deprecated+build.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar"

fetch bettermodel-3.6.0-paper.jar 66b2acc679ffcb81d9d7de686092d3e1daa38dc154da4cdaa9aaabc048a06aba https://github.com/toxicity188/BetterModel/releases/download/3.6.0/bettermodel-3.6.0-paper.jar
fetch packetevents-spigot-2.14.0.jar 060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7 https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar
fetch geyser-spigot-2.12.1-b1298.jar 4e5740267f8f7e1c0b5d56213e047cfdba57a93c322ace63c05286a5d486c66c https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds/1298/downloads/spigot

"$task_java_home/bin/javac" --release 25 -sourcepath '' -cp '.deps/*' -d build/classes src/main/java/de/ark/dinos/*.java
"$task_java_home/bin/javac" --release 25 -cp build/classes -d build/test-classes src/test/java/de/ark/dinos/*.java
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.TamingRulesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.RidingRulesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.SeatRulesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.GroundSpeedsTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.SwimRulesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.DinoSizesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.WildSpawnRulesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.SpeciesRulesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.FlightRulesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.DinoPassengerOrderTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.DinoGrowthTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.CareRulesTest
"$task_java_home/bin/java" -cp build/classes:build/test-classes de.ark.dinos.EggAssetsTest src/main/resources/egg-assets.properties
"$task_java_home/bin/jar" --create --date 2026-11-03T00:00:00Z --file build/ArkDinos-1.2.1.jar -C build/classes . -C src/main/resources .
shasum -a 256 build/ArkDinos-1.2.1.jar
