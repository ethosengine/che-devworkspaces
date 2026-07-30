# podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/rust-nix-dev:latest .
# podman push harbor.ethosengine.com/devspaces/rust-nix-dev:latest

# ============================================================================
# Rust + Holochain Development Container for Eclipse Che/DevSpaces
# ============================================================================
#
# CRITICAL: This image is designed for Eclipse Che/DevSpaces where:
# - PVCs completely REPLACE mounted directories (e.g., ~/.cargo becomes empty)
# - Containers run with arbitrary UIDs (not necessarily 1000)
# - UDI-plus (UBI10) provides DevSpaces integration, Claude Code, Java 21, etc.
#
# Architecture:
# - Rust toolchain installed to /opt/rust (survives PVC mounts on /home/user)
# - Holochain binaries pre-downloaded to /opt/holochain
# - Nix installer cached for optional use (not required for Rust)
#
# Holochain binaries work because UBI10 provides GLIBCXX_3.4.32 (requires 3.4.30)
# ============================================================================

FROM harbor.ethosengine.com/devspaces/udi-plus:latest

USER root

# Install system dependencies for Rust development
RUN dnf install -y \
    xz \
    perl \
    perl-Digest-SHA \
    perl-IPC-Cmd \
    git \
    gcc \
    gcc-c++ \
    make \
    cmake \
    clang-libs \
    openssl-devel \
    zlib-devel \
    pkg-config \
    && dnf clean all

# bindgen (used by wasmer and datachannel-sys) loads libclang.so at build time.
# UBI10 installs it to /usr/lib64; set LIBCLANG_PATH so bindgen finds it without
# relying on ldconfig state inside the container.
ENV LIBCLANG_PATH=/usr/lib64

# Install just command runner
RUN curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh \
    | bash -s -- --to /usr/local/bin

# sccache — Rust compilation cache backed by Garage S3 at
# garage.ethosengine.svc.cluster.local:3900 (SCCACHE_BUCKET / SCCACHE_ENDPOINT
# come from the auto-mounted sccache-credentials Secret in the consuming
# namespace; RUSTC_WRAPPER=sccache is set in the consuming devfile, not here).
ARG SCCACHE_VERSION=v0.15.0
ARG SCCACHE_SHA256=782d2b5dd7ae0a55ebe368ab258114d0928d019ac2d949ab85d5d02f3926709e
RUN curl -fsSL -o /tmp/sccache.tar.gz \
      "https://nexus.ethosengine.com/repository/github-releases-proxy/mozilla/sccache/releases/download/${SCCACHE_VERSION}/sccache-${SCCACHE_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
 && echo "${SCCACHE_SHA256}  /tmp/sccache.tar.gz" | sha256sum -c - \
 && tar -xzf /tmp/sccache.tar.gz -C /tmp \
 && install -m 0755 "/tmp/sccache-${SCCACHE_VERSION}-x86_64-unknown-linux-musl/sccache" /usr/local/bin/sccache \
 && rm -rf /tmp/sccache.tar.gz "/tmp/sccache-${SCCACHE_VERSION}-x86_64-unknown-linux-musl" \
 && /usr/local/bin/sccache --version

# ============================================================================
# Rust toolchain (installed to /opt/rust to survive PVC mounts)
# ============================================================================
ENV RUSTUP_HOME=/opt/rust/rustup
ENV CARGO_HOME=/opt/rust/cargo
ENV PATH=/opt/rust/cargo/bin:$PATH

# Create rust directories with proper permissions for arbitrary UID
RUN mkdir -p /opt/rust/rustup /opt/rust/cargo && \
    chown -R user:root /opt/rust && \
    chmod -R g=u /opt/rust

# Install Rust as user
USER user
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | \
    sh -s -- -y --default-toolchain stable --no-modify-path

# Install common Rust components
RUN rustup component add rust-analyzer rustfmt clippy

# Install WASM target for Holochain DNA/zome compilation
RUN rustup target add wasm32-unknown-unknown

# Verify Rust installation
RUN rustc --version && cargo --version && rust-analyzer --version && \
    rustup target list --installed | grep wasm32

# ============================================================================
# Patch datachannel-sys libdatachannel/CMakeLists.txt
# ============================================================================
# Upstream libdatachannel calls find_package(OpenSSL REQUIRED) without first
# calling find_package(ZLIB REQUIRED). datachannel-sys vendors an OpenSSL
# whose CMake config declares ZLIB::ZLIB in its link interface, so the
# configure step fails unless ZLIB has already been found.
#
# We prime the cargo registry so the crate source lands on disk, then patch
# the CMakeLists. Both steps hard-fail on mismatch so a silently-stale patch
# can't rot after a datachannel-sys version bump.
#
# Remove this block when upstream adds find_package(ZLIB REQUIRED):
#   https://github.com/paullouisageneau/libdatachannel
# ============================================================================
#
# The fetch step must use the SAME registry the devspace runtime uses, or
# the crate source lands under a registry/src/<endpoint>-<hash>/ directory
# cargo never reads at runtime and the patch below is inert (caught
# 2026-05-27: sweettest-check failed in udi-plus-mem-rust-nix; same bug
# shape applies here).
#
# That registry is now crates.io DIRECT. The elohim monorepo dropped its
# crates-io -> Nexus source replacement on 2026-07-30 (ecc65384f): our
# Nexus is Sonatype Community Edition, whose 100k-requests/day cap is
# enforced by refusing new components, and breaching it takes down
# first-party PUBLISHING — the one thing only Nexus can do. The proxy no
# longer serves un-cached crates (it 404s them), so mirroring here is both
# path-wrong and broken. The patch step loops over every matching
# libdatachannel/CMakeLists.txt under registry/src so re-introducing a
# mirror later still ends up patched.
RUN mkdir -p /tmp/fetch && \
    cd /tmp/fetch && \
    cargo init --quiet --name fetch-only && \
    cargo add --quiet datachannel-sys@=0.23.0 && \
    cargo fetch --quiet && \
    rm -rf /tmp/fetch

RUN set -e; \
    find /opt/rust/cargo/registry/src \
        -path '*datachannel-sys-0.23.0+0.23.2/libdatachannel/CMakeLists.txt' \
        > /tmp/cml-list; \
    if [ ! -s /tmp/cml-list ]; then \
        echo "datachannel-sys 0.23.0+0.23.2 not found — patch needs re-aiming"; \
        exit 1; \
    fi; \
    while IFS= read -r CML; do \
        if grep -q 'find_package(ZLIB REQUIRED)' "$CML"; then \
            echo "already patched: $CML"; \
        else \
            sed -i 's|^\tfind_package(OpenSSL REQUIRED)$|\tfind_package(ZLIB REQUIRED)\n\tfind_package(OpenSSL REQUIRED)|' "$CML"; \
            if ! grep -q 'find_package(ZLIB REQUIRED)' "$CML"; then \
                echo "patch did not apply: $CML"; \
                exit 1; \
            fi; \
            echo "patched: $CML"; \
        fi; \
    done < /tmp/cml-list; \
    rm -f /tmp/cml-list

USER root

# ============================================================================
# Holochain binaries (pre-built, requires UBI10 for GLIBCXX_3.4.30)
# ============================================================================
ARG HOLOCHAIN_VERSION=0.6.0
ARG LAIR_VERSION=0.5.3
RUN mkdir -p /opt/holochain/bin && \
    curl -sL -o /opt/holochain/bin/holochain \
      "https://nexus.ethosengine.com/repository/github-releases-proxy/holochain/holochain/releases/download/holochain-${HOLOCHAIN_VERSION}/holochain-x86_64-unknown-linux-gnu" && \
    curl -sL -o /opt/holochain/bin/hc \
      "https://nexus.ethosengine.com/repository/github-releases-proxy/holochain/holochain/releases/download/holochain-${HOLOCHAIN_VERSION}/hc-x86_64-unknown-linux-gnu" && \
    curl -sL -o /opt/holochain/bin/hcterm \
      "https://nexus.ethosengine.com/repository/github-releases-proxy/holochain/holochain/releases/download/holochain-${HOLOCHAIN_VERSION}/hcterm-x86_64-unknown-linux-gnu" && \
    curl -sL -o /opt/holochain/bin/lair-keystore \
      "https://nexus.ethosengine.com/repository/github-releases-proxy/holochain/lair/releases/download/lair_keystore-v${LAIR_VERSION}/lair-keystore-v${LAIR_VERSION}-x86_64-unknown-linux-gnu" && \
    chmod +x /opt/holochain/bin/* && \
    chown -R user:root /opt/holochain && \
    chmod -R g=u /opt/holochain

# ============================================================================
# Optional: Nix installer (cached for users who want it)
# ============================================================================
RUN curl -L https://nixos.org/nix/install -o /tmp/nix-installer.sh && \
    chmod +x /tmp/nix-installer.sh

# ============================================================================
# Helper scripts in /home/user/bin
# ============================================================================
RUN mkdir -p /home/user/bin

# Create rust-status helper command
RUN echo '#!/bin/bash' > /home/user/bin/rust-status && \
    echo 'echo "=== Rust Installation ==="' >> /home/user/bin/rust-status && \
    echo 'echo "RUSTUP_HOME: $RUSTUP_HOME"' >> /home/user/bin/rust-status && \
    echo 'echo "CARGO_HOME: $CARGO_HOME"' >> /home/user/bin/rust-status && \
    echo 'echo ""' >> /home/user/bin/rust-status && \
    echo 'if command -v rustc &> /dev/null; then' >> /home/user/bin/rust-status && \
    echo '    rustc --version' >> /home/user/bin/rust-status && \
    echo '    cargo --version' >> /home/user/bin/rust-status && \
    echo '    rustup show' >> /home/user/bin/rust-status && \
    echo 'else' >> /home/user/bin/rust-status && \
    echo '    echo "ERROR: Rust not found in PATH"' >> /home/user/bin/rust-status && \
    echo '    echo "PATH: $PATH"' >> /home/user/bin/rust-status && \
    echo 'fi' >> /home/user/bin/rust-status && \
    echo 'echo ""' >> /home/user/bin/rust-status && \
    echo 'echo "=== Cargo Cache Usage ==="' >> /home/user/bin/rust-status && \
    echo 'du -sh ~/.cargo 2>/dev/null || echo "~/.cargo: empty or not mounted"' >> /home/user/bin/rust-status && \
    echo 'du -sh $CARGO_HOME 2>/dev/null || echo "$CARGO_HOME: not found"' >> /home/user/bin/rust-status && \
    echo 'echo ""' >> /home/user/bin/rust-status && \
    echo 'echo "=== Holochain Binaries ==="' >> /home/user/bin/rust-status && \
    echo 'holochain --version 2>/dev/null || echo "holochain: not found"' >> /home/user/bin/rust-status && \
    echo 'hc --version 2>/dev/null || echo "hc: not found"' >> /home/user/bin/rust-status && \
    echo 'lair-keystore --version 2>/dev/null || echo "lair-keystore: not found"' >> /home/user/bin/rust-status && \
    chmod +x /home/user/bin/rust-status

# Create holo-help helper command
RUN echo '#!/bin/bash' > /home/user/bin/holo-help && \
    echo 'echo "Holochain CLI tools (pre-built binaries in /opt/holochain/bin):"' >> /home/user/bin/holo-help && \
    echo 'echo ""' >> /home/user/bin/holo-help && \
    echo 'echo "  holochain --version    # Conductor version"' >> /home/user/bin/holo-help && \
    echo 'echo "  hc --version           # CLI version"' >> /home/user/bin/holo-help && \
    echo 'echo "  lair-keystore --help   # Key management"' >> /home/user/bin/holo-help && \
    echo 'echo ""' >> /home/user/bin/holo-help && \
    echo 'echo "Scaffolding (P2P Shipyard / DarkSoil):"' >> /home/user/bin/holo-help && \
    echo 'echo "  hc scaffold            # Interactive scaffolding"' >> /home/user/bin/holo-help && \
    echo 'echo "  hc scaffold web-app    # Create new web app"' >> /home/user/bin/holo-help && \
    echo 'echo ""' >> /home/user/bin/holo-help && \
    echo 'echo "Templates: vanilla, lit, svelte, react, vue, headless"' >> /home/user/bin/holo-help && \
    chmod +x /home/user/bin/holo-help

# Create init-nix script for optional Nix installation
RUN echo '#!/bin/bash' > /home/user/bin/init-nix && \
    echo 'set -e' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo 'echo "Installing Nix package manager (optional)..."' >> /home/user/bin/init-nix && \
    echo 'echo "This installs Nix to /nix for additional packages beyond what'\''s in the container."' >> /home/user/bin/init-nix && \
    echo 'echo ""' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo 'if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> /home/user/bin/init-nix && \
    echo '    echo "Nix is already installed!"' >> /home/user/bin/init-nix && \
    echo '    source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/bin/init-nix && \
    echo '    nix --version' >> /home/user/bin/init-nix && \
    echo '    exit 0' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Use cached installer if available' >> /home/user/bin/init-nix && \
    echo 'if [ -f /tmp/nix-installer.sh ]; then' >> /home/user/bin/init-nix && \
    echo '    bash /tmp/nix-installer.sh --no-daemon' >> /home/user/bin/init-nix && \
    echo 'else' >> /home/user/bin/init-nix && \
    echo '    curl -L https://nixos.org/nix/install | sh -s -- --no-daemon' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Configure Nix' >> /home/user/bin/init-nix && \
    echo 'mkdir -p ~/.config/nix' >> /home/user/bin/init-nix && \
    echo 'echo "experimental-features = nix-command flakes" > ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "sandbox = false" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "substituters = https://cache.nixos.org https://holochain-ci.cachix.org" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "trusted-public-keys = cache.nixos.org-1:6NCHdD59X431o0gWypbMrAURkbJ16ZPMQFGspcDShjY= holochain-ci.cachix.org-1:5IUSkZc0aoRS53rfkvH9Kid40NpyjwCMCzwRTXy+QN8=" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "auto-optimise-store = true" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo 'echo ""' >> /home/user/bin/init-nix && \
    echo 'echo "Nix installed! Source the profile to use it:"' >> /home/user/bin/init-nix && \
    echo 'echo "  source ~/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/bin/init-nix && \
    chmod +x /home/user/bin/init-nix

# Add to bashrc
RUN echo '' >> /home/user/.bashrc && \
    echo '# ============================================================================' >> /home/user/.bashrc && \
    echo '# Rust Development Environment' >> /home/user/.bashrc && \
    echo '# ============================================================================' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# Rust toolchain (installed to /opt/rust to survive PVC mounts)' >> /home/user/.bashrc && \
    echo 'export RUSTUP_HOME=/opt/rust/rustup' >> /home/user/.bashrc && \
    echo 'export CARGO_HOME=/opt/rust/cargo' >> /home/user/.bashrc && \
    echo 'export PATH="/opt/rust/cargo/bin:/opt/holochain/bin:/home/user/bin:$PATH"' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# Source Nix profile if installed (optional)' >> /home/user/.bashrc && \
    echo 'if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> /home/user/.bashrc && \
    echo '    source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/.bashrc && \
    echo 'fi' >> /home/user/.bashrc

# Fix permissions for arbitrary UID (Che requirement)
RUN chgrp -R 0 /home/user && \
    chmod -R g=u /home/user && \
    chmod -R g+w /home/user

USER 1000

WORKDIR /projects
