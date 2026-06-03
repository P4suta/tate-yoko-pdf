# Debian trixie slim (Debian 13, stable since 2025-08) + Liberica JDK 25 Full
# Edition (from BellSoft's apt repo). We need the **Full** flavour because it
# ships the `jmods/` directory, which jlink/jpackage consume to assemble the
# bundled JRE for the app-image distribution. The default
# `liberica-openjdk-debian:25` Docker image is Lite (no jmods) and therefore
# unsuitable as a build base for jpackage.
FROM debian:trixie-slim AS dev

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      bash findutils tar gzip git unzip procps curl ca-certificates gnupg \
      fontconfig libfreetype6 fonts-dejavu \
      binutils \
      qpdf \
 && curl -fsSL https://download.bell-sw.com/pki/GPG-KEY-bellsoft \
      | gpg --dearmor -o /usr/share/keyrings/bellsoft.gpg \
 && echo "deb [signed-by=/usr/share/keyrings/bellsoft.gpg] https://apt.bell-sw.com/ stable main" \
      > /etc/apt/sources.list.d/bellsoft.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends bellsoft-java25-full \
 && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/bellsoft-java25-full-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

ARG TYPOS_VERSION=1.47.0
RUN curl -fsSL "https://github.com/crate-ci/typos/releases/download/v${TYPOS_VERSION}/typos-v${TYPOS_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
  | tar -xz -C /usr/local/bin ./typos \
 && chmod +x /usr/local/bin/typos

ARG JUST_VERSION=1.51.0
RUN curl -fsSL "https://github.com/casey/just/releases/download/${JUST_VERSION}/just-${JUST_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
  | tar -xz -C /usr/local/bin just \
 && chmod +x /usr/local/bin/just

ARG DEV_UID=1000
ARG DEV_GID=1000
RUN groupadd -g ${DEV_GID} dev \
 && useradd -m -u ${DEV_UID} -g ${DEV_GID} -s /bin/bash dev \
 && mkdir -p /workspace /home/dev/.gradle \
 && chown -R dev:dev /workspace /home/dev

ENV GRADLE_USER_HOME=/home/dev/.gradle
ENV LANG=C.UTF-8
WORKDIR /workspace
USER dev

ENTRYPOINT []
CMD ["bash"]
