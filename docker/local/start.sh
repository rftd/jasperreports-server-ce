#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
test -s /var/openjrs/keys/.jrsks
test -s /var/openjrs/keys/.jrsksp
test -s /var/openjrs/config/context.xml
cp -r /var/openjrs/config/webapp/. "$CATALINA_HOME/webapps/jasperserver/"
cp /var/openjrs/config/context.xml "$CATALINA_HOME/conf/Catalina/localhost/jasperserver.xml"
cat >> "$CATALINA_HOME/webapps/jasperserver/WEB-INF/classes/jasperreports.properties" <<'PROPERTIES'

net.sf.jasperreports.chrome.enabled=true
net.sf.jasperreports.chrome.executable.path=/usr/bin/chromium
PROPERTIES
exec catalina.sh run
