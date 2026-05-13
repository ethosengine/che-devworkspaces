# podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/udi-plus-mem-rust-nix:latest .
# podman push harbor.ethosengine.com/devspaces/udi-plus-mem-rust-nix:latest

# ============================================================================
# udi-plus + MemPalace + Rust/Nix/Holochain — full-stack workspace image
# ============================================================================
#
# Layering (as requested):
#   quay.io/devfile/base-developer-image:ubi10-latest
#     -> udi-plus            (claude-code, Java 21, Node, Chrome, hadolint, ...)
#       -> udi-plus-mem      (MemPalace, ChromaDB, sentence-transformers, model cache)
#         -> udi-plus-mem-rust-nix   (this image: + Rust toolchain, Holochain, Nix)
#
# CAVEAT — DUPLICATED CONTENT:
#   The Rust/Holochain/Nix install steps below are an exact replica of
#   containers/rust-dev/Dockerfile (post-FROM). When the Rust layer changes,
#   BOTH Dockerfiles must be updated. If this becomes painful, options are:
#     a) Extract the Rust install into a shared shell script that both
#        Dockerfiles RUN (cleanest long-term).
#     b) Flip the layering: have this image FROM rust-nix-dev and add mem on
#        top — but that contradicts the naming and the mem-layer ordering.
#     c) Use COPY --from=harbor.ethosengine.com/devspaces/rust-nix-dev:latest
#        to graft /opt/rust + /opt/holochain across — fragile, still needs
#        dnf packages + bashrc inline.
#   Sticking with literal duplication for now per request.
# ============================================================================

ARG BASE_TAG=latest
FROM harbor.ethosengine.com/devspaces/udi-plus-mem:${BASE_TAG}

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
ENV LIBCLANG_PATH=/usr/lib64

# Install just command runner
RUN curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh \
    | bash -s -- --to /usr/local/bin

# sccache — Rust compilation cache backed by Garage S3 (creds via consuming devfile).
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

RUN mkdir -p /opt/rust/rustup /opt/rust/cargo && \
    chown -R user:root /opt/rust && \
    chmod -R g=u /opt/rust

USER user
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | \
    sh -s -- -y --default-toolchain stable --no-modify-path

RUN rustup component add rust-analyzer rustfmt clippy
RUN rustup target add wasm32-unknown-unknown

RUN rustc --version && cargo --version && rust-analyzer --version && \
    rustup target list --installed | grep wasm32

# ============================================================================
# Patch datachannel-sys libdatachannel/CMakeLists.txt
# Upstream issue: find_package(OpenSSL REQUIRED) called without ZLIB first.
# Remove when upstream fixes: https://github.com/paullouisageneau/libdatachannel
# ============================================================================
RUN mkdir -p /tmp/fetch && cd /tmp/fetch && \
    cargo init --quiet --name fetch-only && \
    cargo add --quiet datachannel-sys@=0.23.0 && \
    cargo fetch --quiet && \
    rm -rf /tmp/fetch

RUN set -e; \
    CML=$(find /opt/rust/cargo/registry/src \
            -path '*datachannel-sys-0.23.0+0.23.2/libdatachannel/CMakeLists.txt' \
            -print -quit); \
    if [ -z "$CML" ]; then \
        echo "datachannel-sys 0.23.0+0.23.2 not found — patch needs re-aiming"; \
        exit 1; \
    fi; \
    if grep -q 'find_package(ZLIB REQUIRED)' "$CML"; then \
        echo "already patched"; \
    else \
        sed -i 's|^\tfind_package(OpenSSL REQUIRED)$|\tfind_package(ZLIB REQUIRED)\n\tfind_package(OpenSSL REQUIRED)|' "$CML"; \
        if ! grep -q 'find_package(ZLIB REQUIRED)' "$CML"; then \
            echo "patch did not apply"; \
            exit 1; \
        fi; \
    fi

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
    echo 'if [ -f /tmp/nix-installer.sh ]; then' >> /home/user/bin/init-nix && \
    echo '    bash /tmp/nix-installer.sh --no-daemon' >> /home/user/bin/init-nix && \
    echo 'else' >> /home/user/bin/init-nix && \
    echo '    curl -L https://nixos.org/nix/install | sh -s -- --no-daemon' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
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
    echo 'export RUSTUP_HOME=/opt/rust/rustup' >> /home/user/.bashrc && \
    echo 'export CARGO_HOME=/opt/rust/cargo' >> /home/user/.bashrc && \
    echo 'export PATH="/opt/rust/cargo/bin:/opt/holochain/bin:/home/user/bin:$PATH"' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo 'if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> /home/user/.bashrc && \
    echo '    source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/.bashrc && \
    echo 'fi' >> /home/user/.bashrc

# Fix permissions for arbitrary UID (Che requirement)
RUN chgrp -R 0 /home/user && \
    chmod -R g=u /home/user && \
    chmod -R g+w /home/user

USER 1000

WORKDIR /projects
