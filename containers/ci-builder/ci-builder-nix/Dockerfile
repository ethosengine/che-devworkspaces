# CI Builder with Nix for Holochain DNA builds
#
# Extends ci-builder with Nix package manager for Holochain development.
# Uses Determinate Systems installer for reliable multi-user Nix setup.
#
# Build:
#   podman build -t harbor.ethosengine.com/ethosengine/ci-builder-nix:latest -f Dockerfile.ci-builder-nix .
#   podman push harbor.ethosengine.com/ethosengine/ci-builder-nix:latest
#
# Or via Jenkins (preferred)

FROM harbor.ethosengine.com/ethosengine/ci-builder:latest

# python3-nacl + python3-websockets — arms the signal-bus leg of
# scripts/ci/substrate-seam-smoke.sh (elohim repo), which drives
# doorway/doorway-service/tools/sbd-cross-relay-probe.py from this
# 'builder' container in the edge pipeline. Debian apt packages (not pip)
# because bookworm's system Python is PEP 668 externally-managed — apt
# resolves python3 itself as a transitive dependency, no venv needed.
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3-nacl \
    python3-websockets \
    && rm -rf /var/lib/apt/lists/*

# Install Nix via the Determinate Systems nix-installer.
#
# Pin the version + sha256-verify the binary instead of curl-piping the
# wrapper at install.determinate.systems. The wrapper sets its own
# --speed-limit/--speed-time on the inner curl call and aborts on slow
# links (caught us 2026-05-09: build #4 on main, 117s in, "Less than
# 250000 bytes/sec transferred the last 15 seconds"). Direct download
# from GitHub releases lets us control retry behavior.
ARG NIX_INSTALLER_VERSION=v3.20.0
ARG NIX_INSTALLER_SHA256=afaf0c1f426a08967aef4c7f50bbb6282a9312f945775bdf0a0110642339d9d6
RUN curl -fsSL --retry 5 --retry-delay 5 --retry-max-time 300 \
        -o /tmp/nix-installer \
        "https://github.com/DeterminateSystems/nix-installer/releases/download/${NIX_INSTALLER_VERSION}/nix-installer-x86_64-linux" \
 && echo "${NIX_INSTALLER_SHA256}  /tmp/nix-installer" | sha256sum -c - \
 && chmod +x /tmp/nix-installer \
 && /tmp/nix-installer install linux --no-confirm --init none \
 && rm -f /tmp/nix-installer

# Configure Nix for Holochain builds
RUN mkdir -p /etc/nix && \
    echo "experimental-features = nix-command flakes" >> /etc/nix/nix.conf && \
    echo "substituters = https://cache.nixos.org https://holochain-ci.cachix.org" >> /etc/nix/nix.conf && \
    echo "trusted-public-keys = cache.nixos.org-1:6NCHdD59X431o0gWypbMrAURkbJ16ZPMQFGspcDShjY= holochain-ci.cachix.org-1:5IUSkZc0aoRS53rfkvH9Kid40NpyjwCMCzwRTXy+QN8=" >> /etc/nix/nix.conf

# Add Nix to PATH
ENV PATH="/nix/var/nix/profiles/default/bin:${PATH}"

# Verify Nix installation
RUN nix --version

# sccache — Rust compilation cache backed by Garage S3 at
# garage.ethosengine.svc.cluster.local:3900 (SCCACHE_BUCKET / SCCACHE_ENDPOINT
# come from the auto-mounted sccache-credentials Secret in the consuming
# namespace; RUSTC_WRAPPER=sccache is set in the consuming devfile, not here).
ARG SCCACHE_VERSION=v0.15.0
ARG SCCACHE_SHA256=782d2b5dd7ae0a55ebe368ab258114d0928d019ac2d949ab85d5d02f3926709e
RUN curl -fsSL -o /tmp/sccache.tar.gz \
      "https://github.com/mozilla/sccache/releases/download/${SCCACHE_VERSION}/sccache-${SCCACHE_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
 && echo "${SCCACHE_SHA256}  /tmp/sccache.tar.gz" | sha256sum -c - \
 && tar -xzf /tmp/sccache.tar.gz -C /tmp \
 && install -m 0755 "/tmp/sccache-${SCCACHE_VERSION}-x86_64-unknown-linux-musl/sccache" /usr/local/bin/sccache \
 && rm -rf /tmp/sccache.tar.gz "/tmp/sccache-${SCCACHE_VERSION}-x86_64-unknown-linux-musl" \
 && /usr/local/bin/sccache --version

# cargo-nextest — next-gen Rust test runner (https://nexte.st). Distributed as
# a single statically-linked musl binary, so it installs cleanly here even
# though this image has no rustup/cargo of its own — the toolchain comes from
# elohim's `nix develop` shell at job time, and cargo will find the
# `cargo-nextest` subcommand on PATH. Upstream does not publish sha256 sums;
# the pin below was captured from the GitHub release tarball at add time and
# must be refreshed when bumping NEXTEST_VERSION.
ARG NEXTEST_VERSION=0.9.135
ARG NEXTEST_SHA256=c485608adb7ed4a81abdbe432b7fe47d053fca45d645d1786f4e97aa8344cdc1
RUN curl -fsSL -o /tmp/cargo-nextest.tar.gz \
      "https://github.com/nextest-rs/nextest/releases/download/cargo-nextest-${NEXTEST_VERSION}/cargo-nextest-${NEXTEST_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
 && echo "${NEXTEST_SHA256}  /tmp/cargo-nextest.tar.gz" | sha256sum -c - \
 && tar -xzf /tmp/cargo-nextest.tar.gz -C /usr/local/bin/ cargo-nextest \
 && chmod 0755 /usr/local/bin/cargo-nextest \
 && rm /tmp/cargo-nextest.tar.gz

# Keep container running
CMD ["tail", "-f", "/dev/null"]
