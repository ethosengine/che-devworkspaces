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
    openssl-devel \
    pkg-config \
    && dnf clean all

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

# Verify Rust installation
RUN rustc --version && cargo --version && rust-analyzer --version

USER root

# ============================================================================
# Holochain binaries (pre-built, requires UBI10 for GLIBCXX_3.4.30)
# ============================================================================
ARG HOLOCHAIN_VERSION=0.6.0
ARG LAIR_VERSION=0.5.3
RUN mkdir -p /opt/holochain/bin && \
    curl -sL -o /opt/holochain/bin/holochain \
      "https://github.com/holochain/holochain/releases/download/holochain-${HOLOCHAIN_VERSION}/holochain-x86_64-unknown-linux-gnu" && \
    curl -sL -o /opt/holochain/bin/hc \
      "https://github.com/holochain/holochain/releases/download/holochain-${HOLOCHAIN_VERSION}/hc-x86_64-unknown-linux-gnu" && \
    curl -sL -o /opt/holochain/bin/hcterm \
      "https://github.com/holochain/holochain/releases/download/holochain-${HOLOCHAIN_VERSION}/hcterm-x86_64-unknown-linux-gnu" && \
    curl -sL -o /opt/holochain/bin/lair-keystore \
      "https://github.com/holochain/lair/releases/download/lair_keystore-v${LAIR_VERSION}/lair-keystore-v${LAIR_VERSION}-x86_64-unknown-linux-gnu" && \
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
RUN cat > /home/user/bin/rust-status << 'SCRIPT'
#!/bin/bash
echo "=== Rust Installation ==="
echo "RUSTUP_HOME: $RUSTUP_HOME"
echo "CARGO_HOME: $CARGO_HOME"
echo ""
if command -v rustc &> /dev/null; then
    rustc --version
    cargo --version
    rustup show
else
    echo "ERROR: Rust not found in PATH"
    echo "PATH: $PATH"
fi
echo ""
echo "=== Cargo Cache Usage ==="
du -sh ~/.cargo 2>/dev/null || echo "~/.cargo: empty or not mounted"
du -sh $CARGO_HOME 2>/dev/null || echo "$CARGO_HOME: not found"
echo ""
echo "=== Holochain Binaries ==="
holochain --version 2>/dev/null || echo "holochain: not found"
hc --version 2>/dev/null || echo "hc: not found"
lair-keystore --version 2>/dev/null || echo "lair-keystore: not found"
SCRIPT

RUN chmod +x /home/user/bin/rust-status

# Create holo-help helper command
RUN cat > /home/user/bin/holo-help << 'SCRIPT'
#!/bin/bash
echo "Holochain CLI tools (pre-built binaries in /opt/holochain/bin):"
echo ""
echo "  holochain --version    # Conductor version"
echo "  hc --version           # CLI version"
echo "  lair-keystore --help   # Key management"
echo ""
echo "Scaffolding (P2P Shipyard / DarkSoil):"
echo "  hc scaffold            # Interactive scaffolding"
echo "  hc scaffold web-app    # Create new web app"
echo ""
echo "Templates: vanilla, lit, svelte, react, vue, headless"
SCRIPT

RUN chmod +x /home/user/bin/holo-help

# Create init-nix script for optional Nix installation
RUN cat > /home/user/bin/init-nix << 'SCRIPT'
#!/bin/bash
set -e

echo "Installing Nix package manager (optional)..."
echo "This installs Nix to /nix for additional packages beyond what's in the container."
echo ""

if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then
    echo "Nix is already installed!"
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"
    nix --version
    exit 0
fi

# Use cached installer if available
if [ -f /tmp/nix-installer.sh ]; then
    bash /tmp/nix-installer.sh --no-daemon
else
    curl -L https://nixos.org/nix/install | sh -s -- --no-daemon
fi

# Configure Nix
mkdir -p ~/.config/nix
cat > ~/.config/nix/nix.conf << 'EOF'
experimental-features = nix-command flakes
sandbox = false
substituters = https://cache.nixos.org https://holochain-ci.cachix.org
trusted-public-keys = cache.nixos.org-1:6NCHdD59X431o0gWypbMrAURkbJ16ZPMQFGspcDShjY= holochain-ci.cachix.org-1:5IUSkZc0aoRS53rfkvH9Kid40NpyjwCMCzwRTXy+QN8=
auto-optimise-store = true
EOF

echo ""
echo "Nix installed! Source the profile to use it:"
echo "  source ~/.nix-profile/etc/profile.d/nix.sh"
SCRIPT

RUN chmod +x /home/user/bin/init-nix

# Add to bashrc
RUN cat >> /home/user/.bashrc << 'BASHRC'

# ============================================================================
# Rust Development Environment
# ============================================================================

# Rust toolchain (installed to /opt/rust to survive PVC mounts)
export RUSTUP_HOME=/opt/rust/rustup
export CARGO_HOME=/opt/rust/cargo
export PATH="/opt/rust/cargo/bin:/opt/holochain/bin:/home/user/bin:$PATH"

# Source Nix profile if installed (optional)
if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"
fi
BASHRC

# Fix permissions for arbitrary UID (Che requirement)
RUN chgrp -R 0 /home/user && \
    chmod -R g=u /home/user && \
    chmod -R g+w /home/user

USER 1000

WORKDIR /projects
