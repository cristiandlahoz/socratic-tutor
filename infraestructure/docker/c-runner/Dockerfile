FROM gcc:15.2.0-bookworm

LABEL org.opencontainers.image.title="socratic-tutor-c-runner" \
      org.opencontainers.image.description="Sandbox image for GCC syntax checks and GDB debug snapshots"

WORKDIR /workspace

RUN set -eux; \
    apt-get update \
    && apt-get install -y --no-install-recommends gdb \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
