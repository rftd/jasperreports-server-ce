# syntax=docker.io/docker/dockerfile:1
# Local development image. Builds require strict WAR dependency verification.
FROM docker.io/library/node:14.21.3-bullseye AS frontend
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ zip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY scripts/prepare-legacy-frontend.py scripts/prepare-legacy-frontend.py
COPY jasperserver-ui jasperserver-ui
RUN python3 scripts/prepare-legacy-frontend.py --prepare
WORKDIR /src/jasperserver-ui/ce
ENV NODE_OPTIONS=--max-old-space-size=4096 npm_config_python=python3 BUILD_ARTIFACT_VERSION_NAME=ci
RUN yarn install --non-interactive
RUN yarn run clean && yarn run build \
    && cd jrs-ui && yarn run overlay \
    && cd build/overlay && zip -qr /tmp/jrs-ui-overlay.zip .

FROM docker.io/library/eclipse-temurin:8-jdk-jammy AS java8
FROM docker.io/library/maven:3.9.9-eclipse-temurin-8 AS maven-bin
FROM docker.io/library/tomcat:9.0-jdk8-temurin AS tomcat-bin

FROM docker.io/library/debian:bookworm-slim AS build-tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    ant ant-contrib ca-certificates curl fontconfig fonts-dejavu-core \
    postgresql-client python3 unzip util-linux \
    && rm -rf /var/lib/apt/lists/*
COPY --from=java8 /opt/java/openjdk /opt/java/openjdk
COPY --from=maven-bin /usr/share/maven /usr/share/maven
ENV JAVA_HOME=/opt/java/openjdk MAVEN_HOME=/usr/share/maven
ENV PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:$PATH
ENV BUILDOMATIC_ENV=ci MAVEN_OPTS=-Xmx2048m

FROM build-tools AS builder
WORKDIR /src
COPY jasperserver jasperserver
COPY scripts/verify-war-deps.py scripts/verify-war-deps.py
COPY scripts/verify-no-vendor-heartbeat.py scripts/verify-no-vendor-heartbeat.py
COPY scripts/verify-native-reports.py scripts/verify-native-reports.py
COPY docker/local /opt/openjrs-docker
COPY --from=frontend /tmp/jrs-ui-overlay.zip /tmp/jrs-ui-overlay.zip
RUN --mount=type=cache,id=openjrs-maven,target=/root/.m2,sharing=locked \
    bash /opt/openjrs-docker/build.sh \
    && cp -a /root/.m2/repository /opt/maven-repository

FROM builder AS initializer
RUN mkdir -p /root/.m2 && ln -s /opt/maven-repository /root/.m2/repository
WORKDIR /src/jasperserver/buildomatic
ENTRYPOINT ["bash", "/opt/openjrs-docker/initialize.sh"]

FROM docker.io/library/debian:bookworm-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates chromium chromium-sandbox curl fontconfig fonts-dejavu-core tini \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 openjrs \
    && useradd --uid 10001 --gid openjrs --create-home openjrs
COPY --from=java8 /opt/java/openjdk /opt/java/openjdk
COPY --from=tomcat-bin /usr/local/tomcat /usr/local/tomcat
ENV JAVA_HOME=/opt/java/openjdk CATALINA_HOME=/usr/local/tomcat
ENV PATH=/opt/java/openjdk/bin:/usr/local/tomcat/bin:$PATH
ENV CATALINA_OPTS="-Xms256m -Xmx2048m -Djava.awt.headless=true"
ENV ks=/var/openjrs/keys ksp=/var/openjrs/keys
RUN rm -rf /usr/local/tomcat/webapps/* /usr/local/tomcat/webapps.dist \
    && mkdir -p /usr/local/tomcat/conf/Catalina/localhost
COPY --from=builder /opt/openjrs-webapp /usr/local/tomcat/webapps/jasperserver
COPY --from=builder /src/jasperserver/buildomatic/conf_source/db/postgresql/jdbc/postgresql-42.5.4.jar /usr/local/tomcat/lib/
COPY docker/local/start.sh /usr/local/bin/openjrs-start
RUN chown -R openjrs:openjrs /usr/local/tomcat
USER openjrs
WORKDIR /usr/local/tomcat
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=20 \
    CMD curl --fail --silent http://127.0.0.1:8080/jasperserver/login.html > /dev/null || exit 1
ENTRYPOINT ["/usr/bin/tini", "--", "bash", "/usr/local/bin/openjrs-start"]
