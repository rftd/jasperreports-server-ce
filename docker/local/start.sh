#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
test -s /var/openjrs/keys/.jrsks
test -s /var/openjrs/keys/.jrsksp
test -s /var/openjrs/config/context.xml
cp -r /var/openjrs/config/webapp/. "$CATALINA_HOME/webapps/jasperserver/"
cp /var/openjrs/config/context.xml "$CATALINA_HOME/conf/Catalina/localhost/jasperserver.xml"
# The server's Spring context derives both chrome.enabled and executable.path
# from this setting, overriding the JasperReports classpath defaults. Set it
# only in the runtime, where Chromium is installed, after copying shared config.
printf '\nchrome.path=/usr/bin/chromium\n' >> "$CATALINA_HOME/webapps/jasperserver/WEB-INF/js.config.properties"
exec catalina.sh run
