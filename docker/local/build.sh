#!/usr/bin/env bash
set -Eeuo pipefail
export OPENJRS_DB_PASSWORD=build-placeholder
export ks=/tmp/openjrs-build-keys ksp=/tmp/openjrs-build-keys
mkdir -p "$ks"
python3 /opt/openjrs-docker/configure.py
mvn -B -ntp org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file \
  -Dfile=/tmp/jrs-ui-overlay.zip -DgroupId=com.jaspersoft -DartifactId=jrs-ui \
  -Dversion=ci -Dpackaging=zip -DgeneratePom=true
cd /src/jasperserver/buildomatic
./js-ant create-keystore
mvn -B -ntp -f /src/jasperserver/pom.xml -DbuildArtifactVersionName=ci \
  -Dji-repo-releases=https://jaspersoft.jfrog.io/jaspersoft/third-party-ce-artifacts \
  -Dji-repo-snapshots=https://jaspersoft.jfrog.io/jaspersoft/jr-ce-snapshots \
  -pl jasperserver-custom-visualization -am test
./js-ant build-ce 2>&1 | tee /tmp/backend-build.log
# Ant must succeed with the reviewed inventory; no build failure is tolerated.
python3 /src/scripts/verify-war-deps.py \
  --master /src/jasperserver/deps/master-war-deps-ce.txt \
  --current /src/jasperserver/target/deps/current-war-deps-ce.txt \
  --war /src/jasperserver/jasperserver-war/target/jasperserver.war \
  --project-version ci
mvn -B -ntp -f /src/jasperserver/jasperserver-war-jar/pom.xml \
  -s build_conf/default/maven_settings_community.xml \
  -DskipTests=false -Dtest=JSCommonControllerTest,ViewerActionMappingsTest,InteractiveTableActionsTest test
unzip -t /src/jasperserver/jasperserver-war/target/jasperserver.war >/dev/null
python3 /src/scripts/verify-no-vendor-heartbeat.py /src/jasperserver/jasperserver-war/target/jasperserver.war
python3 /src/scripts/verify-native-reports.py --war /src/jasperserver/jasperserver-war/target/jasperserver.war
mkdir -p /opt/openjrs-webapp
unzip -q /src/jasperserver/jasperserver-war/target/jasperserver.war -d /opt/openjrs-webapp
rm -rf "$ks"
rm -f keystore.init.properties
