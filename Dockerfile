ARG GRAALVM_IMAGE=ghcr.io/graalvm/native-image-community:25

FROM ${GRAALVM_IMAGE} AS dev

RUN (microdnf install -y findutils tar gzip git unzip which procps-ng shadow-utils curl && microdnf clean all) \
 || (dnf install -y findutils tar gzip git unzip which procps-ng shadow-utils curl && dnf clean all) \
 || (yum install -y findutils tar gzip git unzip which procps-ng shadow-utils curl && yum clean all)

ARG TYPOS_VERSION=1.46.3
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


FROM ${GRAALVM_IMAGE} AS builder

RUN (microdnf install -y findutils tar gzip unzip && microdnf clean all) \
 || (dnf install -y findutils tar gzip unzip && dnf clean all) \
 || (yum install -y findutils tar gzip unzip && yum clean all)

WORKDIR /build
COPY --chown=root:root gradle ./gradle
COPY --chown=root:root gradlew settings.gradle.kts build.gradle.kts ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY --chown=root:root src ./src
ENTRYPOINT []
RUN ./gradlew --no-daemon nativeCompile


FROM debian:stable-slim AS runtime

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates xdg-utils \
 && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/build/native/nativeCompile/tate-yoko-pdf /usr/local/bin/tate-yoko-pdf

ENTRYPOINT ["/usr/local/bin/tate-yoko-pdf"]
