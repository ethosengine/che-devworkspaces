# containers/holochain-ci-builder/Dockerfile
# Build: podman build --format docker -t harbor.ethosengine.com/ethosengine/ci-builder-holochain:latest .
# Push:  podman push harbor.ethosengine.com/ethosengine/ci-builder-holochain:latest

FROM node:20-bookworm AS base

# --- System deps (browsers, build tools, Java for Sonar, etc.)
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium xauth xvfb x11-utils libgtk-3-0 libgbm-dev libnss3 libxss1 libasound2 libxtst6 libnotify-dev \
    curl wget git bash unzip sed coreutils ca-certificates openssh-client \
    build-essential pkg-config libssl-dev xz-utils \
    openjdk-17-jre-headless \
 && rm -rf /var/lib/apt/lists/*

SHELL ["/bin/bash", "-lc"]

# --- kubectl (client)
RUN curl -fsSL "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" \
    -o /usr/local/bin/kubectl && chmod +x /usr/local/bin/kubectl

# --- nerdctl + buildctl (for BuildKit builds in CI)
ARG NERDCTL_VERSION=1.7.7
ARG BUILDKIT_VERSION=0.12.5
RUN curl -fsSL "https://github.com/containerd/nerdctl/releases/download/v${NERDCTL_VERSION}/nerdctl-${NERDCTL_VERSION}-linux-amd64.tar.gz" \
    | tar -xz -C /usr/local/bin nerdctl \
 && chmod +x /usr/local/bin/nerdctl \
 && curl -fsSL "https://github.com/moby/buildkit/releases/download/v${BUILDKIT_VERSION}/buildkit-v${BUILDKIT_VERSION}.linux-amd64.tar.gz" \
    | tar -xz -C /tmp \
 && mv /tmp/bin/buildctl /usr/local/bin/buildctl \
 && chmod +x /usr/local/bin/buildctl \
 && rm -rf /tmp/bin

# --- Chromium wrapper for CI (no-sandbox etc.)
RUN printf '%s\n' \
  '#!/usr/bin/env bash' \
  'exec /usr/bin/chromium --no-sandbox --disable-dev-shm-usage --disable-gpu "$@"' \
  > /usr/local/bin/chromium-wrapper && chmod +x /usr/local/bin/chromium-wrapper
ENV CHROME_BIN=/usr/local/bin/chromium-wrapper \
    CHROME_PATH=/usr/local/bin/chromium-wrapper \
    DISPLAY=:99

# --- Rust toolchain + wasm target (for zome builds)
ARG RUST_VERSION=1.75.0
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \
    | sh -s -- -y --profile minimal --default-toolchain ${RUST_VERSION} --target wasm32-unknown-unknown
ENV CARGO_HOME=/root/.cargo RUSTUP_HOME=/root/.rustup PATH="/root/.cargo/bin:${PATH}"

# --- Bun runtime
ENV BUN_INSTALL=/root/.bun
RUN curl -fsSL https://bun.sh/install | bash
ENV PATH="${BUN_INSTALL}/bin:${PATH}"

# =====================================================================================
# NIX + HOLONIX (build as NON-ROOT user to avoid chown failures under user namespaces)
# =====================================================================================

# Container-wide nix config (as root). Flakes on; sandboxing relaxed for containers.
RUN mkdir -p /etc/nix \
 && printf '%s\n' \
      'experimental-features = nix-command flakes' \
      'sandbox = false' \
      'filter-syscalls = false' \
   >> /etc/nix/nix.conf

# Use the existing node user for all Nix work (avoids tar/chown UID mapping issues)
# Install Nix for the node user (single-user install)
RUN runuser -l node -c 'mkdir -p ~/.config/nix && \
  printf "experimental-features = nix-command flakes\n" > ~/.config/nix/nix.conf && \
  curl -fsSL https://nixos.org/nix/install | sh -s -- --no-daemon'

# Ensure nix is on PATH for both root and the node user
ENV PATH="/nix/var/nix/profiles/default/bin:/home/node/.nix-profile/bin:/root/.nix-profile/bin:${PATH}"

# Prove nix exists (as node user)
RUN runuser -l node -c '. ~/.nix-profile/etc/profile.d/nix.sh && nix --version'

# Install Holochain tools from Holonix flake (PINNED to main-0.5) as node user
RUN runuser -l node -c '. ~/.nix-profile/etc/profile.d/nix.sh && \
  nix --extra-experimental-features "nix-command flakes" profile install \
    "github:holochain/holonix?ref=main-0.5#holochain" \
    "github:holochain/holonix?ref=main-0.5#hc" \
    "github:holochain/holonix?ref=main-0.5#lair-keystore"'

# Make Holochain tools visible system-wide without sourcing (nice for CI scripts)
RUN ln -sf /home/node/.nix-profile/bin/holochain /usr/local/bin/holochain && \
    ln -sf /home/node/.nix-profile/bin/hc        /usr/local/bin/hc && \
    ln -sf /home/node/.nix-profile/bin/lair-keystore /usr/local/bin/lair-keystore

# Sanity check (fail early if something's off)
RUN holochain --build-info && hc --version && lair-keystore --version

# --- (Optional) Sonar scanner CLI
RUN npm install -g sonarqube-scanner

# --- Helper scripts
RUN printf '%s\n' \
'#!/usr/bin/env bash' \
'set -euo pipefail' \
'# Prefer node user env if present' \
'if [ -f /home/node/.nix-profile/etc/profile.d/nix.sh ]; then . /home/node/.nix-profile/etc/profile.d/nix.sh; fi' \
'export PATH="/nix/var/nix/profiles/default/bin:/home/node/.nix-profile/bin:/root/.nix-profile/bin:${PATH}"' \
'# Start Xvfb for headless browser tests if not already running' \
'if ! pgrep -x Xvfb >/dev/null 2>&1; then Xvfb :99 -screen 0 1024x768x24 >/dev/null 2>&1 & fi' \
'exec "$@"' \
> /usr/local/bin/with-env && chmod +x /usr/local/bin/with-env

RUN printf '%s\n' \
'#!/usr/bin/env bash' \
'set -e' \
'echo "🔎 Environment check:"' \
'node --version || true' \
'bun --version || true' \
'rustc --version || true' \
'cargo --version || true' \
'kubectl version --client --short || true' \
'nerdctl --version || true' \
'echo -n "holochain: " && (holochain --version || echo "not found")' \
'echo -n "hc:        " && (hc --version || echo "not found")' \
'echo -n "lair:      " && (lair-keystore --version || echo "not found")' \
'echo "✅ Environment ready"' \
> /usr/local/bin/check-env && chmod +x /usr/local/bin/check-env

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/with-env"]
CMD ["check-env"]