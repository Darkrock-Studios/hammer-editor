# syntax=docker/dockerfile:1
#
# Runtime image for the Hammer sync server. Packages the pre-built application
# distribution rather than building from source: :server depends on :base, which
# needs the Android SDK. Build it first, then the image, from the repo root:
#
#   ./gradlew :server:installDist
#   docker build -f docker/Dockerfile -t hammer-server .

# The embedded PostgreSQL binaries are glibc-only; they will not run on musl.
FROM eclipse-temurin:21-jre-jammy

# fontconfig/libfreetype6 back the optional richLinkPreviews rendering; curl backs the HEALTHCHECK.
RUN apt-get update \
	&& apt-get install -y --no-install-recommends \
		fontconfig \
		libfreetype6 \
		curl \
	&& rm -rf /var/lib/apt/lists/*

# Embedded PostgreSQL refuses to run as root. Fixed uid/gid keeps bind-mount
# permissions predictable.
RUN groupadd --gid 1000 hammer \
	&& useradd --uid 1000 --gid 1000 --home-dir /home/hammer --create-home hammer

# Puts the data directory (<user.home>/hammer_data) on the volume. Deliberately
# SERVER_OPTS, not JAVA_OPTS: the start script appends both, and an operator
# setting JAVA_OPTS for heap would otherwise silently relocate all durable state.
ENV SERVER_OPTS="-Duser.home=/data"

ARG DIST_DIR=server/build/install/server
COPY --chown=1000:1000 ${DIST_DIR}/ /opt/hammer/

# hammer_data itself must exist and be owned here: bind-mounting a config file
# into it would otherwise have Docker create the parent as root, leaving the
# unprivileged server unable to write pgdata.
RUN mkdir -p /data/hammer_data \
	&& chown -R hammer:hammer /data

USER hammer
WORKDIR /opt/hammer

VOLUME ["/data"]

# Plain HTTP. Terminate TLS at a reverse proxy, or set sslCert and publish 443.
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
	CMD curl -fsS http://localhost:8080/ >/dev/null || exit 1

ENTRYPOINT ["/opt/hammer/bin/server"]
