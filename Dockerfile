# Liberica NIK Full ships strong AWT/Swing support out of the box, which the
# stock GraalVM CE image does not — required because PDFBox' PDDocument static
# initialiser pulls in java.awt.image.Raster/ColorModel and the matching JNI libs.
ARG GRAALVM_IMAGE=bellsoft/liberica-native-image-kit-container:jdk-25-nik-25-glibc

FROM ${GRAALVM_IMAGE} AS dev

# Alpaquita Linux ships apk; install AWT/font runtime deps + dev tooling.
RUN apk add --no-cache \
      bash findutils tar gzip git unzip which procps shadow curl \
      fontconfig freetype ttf-dejavu

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

RUN apk add --no-cache bash findutils tar gzip unzip fontconfig freetype

WORKDIR /build
COPY --chown=root:root gradle ./gradle
COPY --chown=root:root gradlew settings.gradle.kts build.gradle.kts ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY --chown=root:root src ./src
ENTRYPOINT []
RUN ./gradlew --no-daemon nativeCompile


# Use the matching BellSoft slim runtime so AWT/font libs match the build env.
FROM bellsoft/liberica-runtime-container:jdk-25-slim-glibc AS runtime

# Font subsystem must be installed even in the slim runtime image.
RUN apk add --no-cache fontconfig freetype ttf-dejavu ca-certificates

# Native binary + JDK shim libraries (libawt.so / libfontmanager.so / liblcms.so / ...)
# emitted by `gradle nativeCompile`. They must live next to the executable; we set
# LD_LIBRARY_PATH in the launcher so the dynamic loader actually finds them.
COPY --from=builder /build/build/native/nativeCompile/ /opt/tate-yoko/

RUN printf '#!/bin/sh\nexec env LD_LIBRARY_PATH=/opt/tate-yoko /opt/tate-yoko/tate-yoko-pdf "$@"\n' \
      > /usr/local/bin/tate-yoko-pdf \
 && chmod +x /usr/local/bin/tate-yoko-pdf

ENTRYPOINT ["/usr/local/bin/tate-yoko-pdf"]
