FROM debian:forky-slim

LABEL org.opencontainers.image.title="socratic-tutor-c-runner" \
      org.opencontainers.image.description="Sandbox image for GCC syntax checks and GDB debug snapshots"

WORKDIR /workspace

RUN set -eux; \
    apt-get update \
    && apt-get install -y --no-install-recommends gcc gdb libc6-dev \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
