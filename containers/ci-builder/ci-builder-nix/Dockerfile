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

# Install Nix using Determinate Systems installer (single-user mode for containers)
# https://github.com/DeterminateSystems/nix-installer
RUN curl --proto '=https' --tlsv1.2 -sSf -L https://install.determinate.systems/nix | \
    sh -s -- install linux --no-confirm --init none

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

# Keep container running
CMD ["tail", "-f", "/dev/null"]
