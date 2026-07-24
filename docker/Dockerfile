# syntax=docker/dockerfile:1
#
# Runtime image for the Hammer sync server.
#
# This image does NOT build the server from source. The server shares the :base
# module with the Android client, so a from-source build needs the full Android
# SDK. Instead we package the already-built application distribution, exactly the
# same artifact shipped as `server.tar`/`server.zip` in a release.
#
# Build the distribution first, then build the image from the repo root:
#
#   ./gradlew :server:installDist
#   docker build -f docker/Dockerfile -t hammer-server .
#
# The distribution is pure JVM bytecode (the embedded PostgreSQL binaries for
# every OS/arch are bundled inside the jars), so one build works on both
# linux/amd64 and linux/arm64.

# A glibc base (Debian/Ubuntu) is required: the embedded PostgreSQL (Zonky)
# binaries are glibc-only and will not run on Alpine/musl.
FROM eclipse-temurin:21-jre-jammy

# fontconfig + libfreetype6 are only needed when `richLinkPreviews` is enabled
# (headless AWT text rendering); they are small, so installing them keeps that
# feature working out of the box. curl backs the HEALTHCHECK below.
RUN apt-get update \
	&& apt-get install -y --no-install-recommends \
		fontconfig \
		libfreetype6 \
		curl \
	&& rm -rf /var/lib/apt/lists/*

# Embedded PostgreSQL refuses to run as root, so the server runs unprivileged.
# A fixed uid/gid keeps host bind-mount permissions predictable.
RUN groupadd --gid 1000 hammer \
	&& useradd --uid 1000 --gid 1000 --home-dir /home/hammer --create-home hammer

# The server derives its data directory from user.home (`<user.home>/hammer_data`).
# Pinning user.home to /data puts the database, caches, keyring, and config under
# a single mount point. `config.toml` placed at /data/hammer_data/config.toml is
# auto-loaded; no --config flag needed.
ENV JAVA_OPTS="-Duser.home=/data"

# Location of the pre-built distribution in the build context.
ARG DIST_DIR=server/build/install/server
COPY ${DIST_DIR}/ /opt/hammer/

# Pre-create the data dir (not just /data) so that a named volume initializes
# with it already owned by `hammer`. Without this, bind-mounting a config file at
# /data/hammer_data/config.toml would make Docker create the parent dir as root,
# leaving the unprivileged server unable to write pgdata into it.
RUN mkdir -p /data/hammer_data \
	&& chown -R hammer:hammer /data /opt/hammer

USER hammer
WORKDIR /opt/hammer

# Persist all durable state (and config) on this volume.
VOLUME ["/data"]

# Plain HTTP connector. Terminate TLS at a reverse proxy in front of the
# container, or configure `sslCert` in config.toml and publish 443 as well.
EXPOSE 8080

# First boot initializes the embedded database, which can take a while, hence the
# generous start period.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
	CMD curl -fsS http://localhost:8080/ >/dev/null || exit 1

# The gradle-generated launcher. Extra args (e.g. --config, --dev, subcommands
# like generate-keyring) can be appended after the image name in `docker run`.
ENTRYPOINT ["/opt/hammer/bin/server"]
