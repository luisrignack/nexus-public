# Multi-stage build: compila el fork desde el código fuente y genera una imagen de ejecución
# que contiene solo la assembly y un JRE.
FROM docker.m.daocloud.io/library/maven:3.9.9-eclipse-temurin-21 AS build

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
 && curl -fsSL https://deb.nodesource.com/setup_24.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && corepack enable \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

# Yarn usa --immutable en el build de Maven; generamos el lockfile una vez.
RUN corepack yarn install

RUN mvn -B -Denforcer.skip=true -Dnode.version=v24.21.0 -DskipTests install -Ppublic \
 && mvn -B -Denforcer.skip=true -Dnode.version=v24.21.0 -DskipTests \
      -pl public/selfhosted/assemblies/nexus-repository-core package -Ppublic

FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r -g 1000 nexus \
 && useradd -r -u 1000 -g nexus -m -d /opt/sonatype/nexus nexus \
 && mkdir -p /nexus-data /opt/sonatype/nexus \
 && chown -R nexus:nexus /nexus-data /opt/sonatype/nexus

COPY --from=build --chown=nexus:nexus \
     /src/public/selfhosted/assemblies/nexus-repository-core/target/assembly \
     /opt/sonatype/nexus
COPY --chown=nexus:nexus docker/nexus-antivirus/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh /opt/sonatype/nexus/bin/nexus

USER nexus
ENV KARAF_DATA=/nexus-data
ENV INSTALL4J_ADD_VM_PARAMS="-Xms1g -Xmx2g -Djava.net.preferIPv4Stack=true"
VOLUME /nexus-data
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 \
  CMD curl -fsS http://localhost:8081/service/rest/v1/status >/dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
