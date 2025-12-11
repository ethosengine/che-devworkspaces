# podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/rust-nix-dev:latest .
# podman push harbor.ethosengine.com/devspaces/rust-nix-dev:latest

# ============================================================================
# Rust + Nix + Holochain Development Container for Eclipse Che/DevSpaces
# ============================================================================
#
# CRITICAL: This image is designed for Eclipse Che/DevSpaces where:
# - PVCs completely REPLACE mounted directories (e.g., /nix becomes empty)
# - Containers run with arbitrary UIDs (not necessarily 1000)
# - UDI-plus (UBI10) provides DevSpaces integration, Claude Code, Java 21, etc.
#
# Architecture:
# - BUILD TIME: Install system deps, scripts, cached installers, holochain binaries
# - RUNTIME: Install Nix/Rust to /nix (on PVC) during first terminal
#
# Holochain binaries are pre-downloaded at build time and work because UBI10
# provides GLIBCXX_3.4.32 (holochain binaries require 3.4.30)
# ============================================================================

FROM harbor.ethosengine.com/devspaces/udi-plus:latest

USER root

# Install system dependencies needed for Nix and Rust development
# These go to /usr, /etc, etc. and are NOT replaced by volume mounts
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

# Pre-download Nix installer to /tmp (speeds up runtime init)
# This survives in the image and avoids download on every workspace
RUN curl -L https://nixos.org/nix/install -o /tmp/nix-installer.sh && \
    chmod +x /tmp/nix-installer.sh

# ============================================================================
# Holochain binaries (pre-built, requires UBI10 for GLIBCXX_3.4.30)
# ============================================================================
# Download pre-built holochain binaries from official releases
# These work on UBI10 because it provides GLIBCXX_3.4.32 via GCC 14.2
# IMPORTANT: Install to /opt/holochain (not /home/user) because Che overwrites
# the home directory with PVC mounts at workspace startup
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

# Create init-nix script (runs at RUNTIME, not build time)
# This will be called by .bashrc on first terminal open
RUN mkdir -p /home/user/bin && \
    echo '#!/bin/bash' > /home/user/bin/init-nix && \
    echo 'set -e' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo 'echo "🔧 Initializing Nix environment..."' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# XDG directories are set by devfile.yaml env vars (injected by Kubernetes)' >> /home/user/bin/init-nix && \
    echo '# Just ensure they exist' >> /home/user/bin/init-nix && \
    echo 'mkdir -p "${XDG_CACHE_HOME:-/tmp/cache}" "${XDG_DATA_HOME:-/tmp/data}" "${XDG_STATE_HOME:-/tmp/state}" "${XDG_CONFIG_HOME:-/tmp/config}"' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# CRITICAL FIX: Marker file in /nix (persists in PVC across restarts)' >> /home/user/bin/init-nix && \
    echo '# Using versioned marker to allow forced re-initialization if needed' >> /home/user/bin/init-nix && \
    echo 'NIX_INIT_MARKER="/nix/.initialized-v4"' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Prevent parallel initialization from multiple shells' >> /home/user/bin/init-nix && \
    echo 'NIX_INIT_LOCK="/nix/.init-lock"' >> /home/user/bin/init-nix && \
    echo 'if [ -f "$NIX_INIT_LOCK" ]; then' >> /home/user/bin/init-nix && \
    echo '    echo "⏳ Another shell is initializing Nix, waiting..."' >> /home/user/bin/init-nix && \
    echo '    for i in {1..30}; do' >> /home/user/bin/init-nix && \
    echo '        sleep 2' >> /home/user/bin/init-nix && \
    echo '        if [ -f "$NIX_INIT_MARKER" ]; then' >> /home/user/bin/init-nix && \
    echo '            echo "✅ Initialization completed by other shell"' >> /home/user/bin/init-nix && \
    echo '            if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> /home/user/bin/init-nix && \
    echo '                source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/bin/init-nix && \
    echo '            fi' >> /home/user/bin/init-nix && \
    echo '            exit 0' >> /home/user/bin/init-nix && \
    echo '        fi' >> /home/user/bin/init-nix && \
    echo '    done' >> /home/user/bin/init-nix && \
    echo '    echo "⚠️  Lock timeout, removing stale lock"' >> /home/user/bin/init-nix && \
    echo '    rm -f "$NIX_INIT_LOCK"' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Check if already initialized' >> /home/user/bin/init-nix && \
    echo 'if [ -f "$NIX_INIT_MARKER" ]; then' >> /home/user/bin/init-nix && \
    echo '    echo "✅ Nix already initialized (marker: $NIX_INIT_MARKER)"' >> /home/user/bin/init-nix && \
    echo '    if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> /home/user/bin/init-nix && \
    echo '        source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/bin/init-nix && \
    echo '    fi' >> /home/user/bin/init-nix && \
    echo '    exit 0' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Check if Nix is working (might be initialized without marker)' >> /home/user/bin/init-nix && \
    echo 'if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ] && [ -x "$HOME/.nix-profile/bin/nix-env" ]; then' >> /home/user/bin/init-nix && \
    echo '    echo "✅ Nix already working, creating marker"' >> /home/user/bin/init-nix && \
    echo '    touch "$NIX_INIT_MARKER" 2>/dev/null || true' >> /home/user/bin/init-nix && \
    echo '    source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/bin/init-nix && \
    echo '    exit 0' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Create lock file' >> /home/user/bin/init-nix && \
    echo 'touch "$NIX_INIT_LOCK" 2>/dev/null || true' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo 'echo "📦 Installing Nix (first time only, this may take 2-3 minutes)..."' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Only clean up if truly corrupt' >> /home/user/bin/init-nix && \
    echo 'if [ -d "$HOME/.nix-profile" ] && [ ! -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> /home/user/bin/init-nix && \
    echo '    echo "⚠️  Cleaning up corrupted Nix profile..."' >> /home/user/bin/init-nix && \
    echo '    rm -rf ~/.nix-profile ~/.nix-defexpr ~/.nix-channels ~/.local/state/nix 2>/dev/null || true' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Install Nix using cached installer (single-user mode for DevSpaces)' >> /home/user/bin/init-nix && \
    echo '# The /nix directory is on the PVC, so this persists across restarts' >> /home/user/bin/init-nix && \
    echo 'if [ -f /tmp/nix-installer.sh ]; then' >> /home/user/bin/init-nix && \
    echo '    bash /tmp/nix-installer.sh --no-daemon' >> /home/user/bin/init-nix && \
    echo 'else' >> /home/user/bin/init-nix && \
    echo '    curl -L https://nixos.org/nix/install | sh -s -- --no-daemon' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Verify Nix installation succeeded' >> /home/user/bin/init-nix && \
    echo 'if [ ! -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> /home/user/bin/init-nix && \
    echo '    echo "❌ ERROR: Nix installation failed - profile not created"' >> /home/user/bin/init-nix && \
    echo '    echo "Please check the installation logs above for errors"' >> /home/user/bin/init-nix && \
    echo '    rm -f "$NIX_INIT_LOCK"' >> /home/user/bin/init-nix && \
    echo '    exit 1' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Source Nix profile' >> /home/user/bin/init-nix && \
    echo 'source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Verify nix commands are available' >> /home/user/bin/init-nix && \
    echo 'if ! command -v nix-env &>/dev/null; then' >> /home/user/bin/init-nix && \
    echo '    echo "❌ ERROR: Nix commands not available after installation"' >> /home/user/bin/init-nix && \
    echo '    rm -f "$NIX_INIT_LOCK"' >> /home/user/bin/init-nix && \
    echo '    exit 1' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Configure Nix for storage optimization' >> /home/user/bin/init-nix && \
    echo 'mkdir -p ~/.config/nix' >> /home/user/bin/init-nix && \
    echo 'echo "# Experimental features" > ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "experimental-features = nix-command flakes" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "# CRITICAL: Disable sandbox for rootless containers (Eclipse Che/DevSpaces)" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "sandbox = false" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "# Holochain binary cache" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "substituters = https://cache.nixos.org https://holochain-ci.cachix.org" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "trusted-public-keys = cache.nixos.org-1:6NCHdD59X431o0gWypbMrAURkbJ16ZPMQFGspcDShjY= holochain-ci.cachix.org-1:5IUSkZc0aoRS53rfkvH9Kid40NpyjwCMCzwRTXy+QN8=" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "# Automatic garbage collection thresholds" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "min-free = 5368709120" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "max-free = 10737418240" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "# Storage optimization" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "auto-optimise-store = true" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "# Don'"'"'t keep unnecessary outputs" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "keep-outputs = false" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo 'echo "keep-derivations = false" >> ~/.config/nix/nix.conf' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Add nixpkgs channel (check if already exists to prevent duplicates)' >> /home/user/bin/init-nix && \
    echo 'if ! nix-channel --list | grep -q '"'"'^nixpkgs'"'"'; then' >> /home/user/bin/init-nix && \
    echo '    echo "📦 Adding nixpkgs channel..."' >> /home/user/bin/init-nix && \
    echo '    nix-channel --add https://nixos.org/channels/nixpkgs-unstable nixpkgs' >> /home/user/bin/init-nix && \
    echo '    nix-channel --update' >> /home/user/bin/init-nix && \
    echo 'else' >> /home/user/bin/init-nix && \
    echo '    echo "✅ nixpkgs channel already configured"' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo 'echo "🦀 Installing Rust toolchain via Nix..."' >> /home/user/bin/init-nix && \
    echo 'nix-env -iA nixpkgs.rustup nixpkgs.gcc nixpkgs.pkg-config nixpkgs.openssl' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Initialize rustup' >> /home/user/bin/init-nix && \
    echo 'rustup default stable' >> /home/user/bin/init-nix && \
    echo 'rustup component add rust-analyzer rustfmt clippy' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Create marker file (prevents re-initialization)' >> /home/user/bin/init-nix && \
    echo 'touch "$NIX_INIT_MARKER"' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo '# Remove lock file' >> /home/user/bin/init-nix && \
    echo 'rm -f "$NIX_INIT_LOCK"' >> /home/user/bin/init-nix && \
    echo '' >> /home/user/bin/init-nix && \
    echo 'echo "✅ Nix and Rust installation complete!"' >> /home/user/bin/init-nix && \
    echo 'echo "📦 Nix profile will be auto-loaded in new shells"' >> /home/user/bin/init-nix && \
    echo 'echo ""' >> /home/user/bin/init-nix && \
    echo 'echo "💡 Helpful commands:"' >> /home/user/bin/init-nix && \
    echo 'echo "  nix-status        - Check storage and health"' >> /home/user/bin/init-nix && \
    echo 'echo "  nix-clean         - Run garbage collection"' >> /home/user/bin/init-nix && \
    echo 'echo "  cargo --version   - Verify Rust installation"' >> /home/user/bin/init-nix

RUN chmod +x /home/user/bin/init-nix

# Create nix-status helper command
RUN echo '#!/bin/bash' > /home/user/bin/nix-status && \
    echo '' >> /home/user/bin/nix-status && \
    echo 'echo "=== Nix Initialization Status ==="' >> /home/user/bin/nix-status && \
    echo 'if [ -f /nix/.initialized-v4 ]; then' >> /home/user/bin/nix-status && \
    echo '    echo "✅ Initialized (marker: /nix/.initialized-v4)"' >> /home/user/bin/nix-status && \
    echo 'else' >> /home/user/bin/nix-status && \
    echo '    echo "❌ Not initialized"' >> /home/user/bin/nix-status && \
    echo 'fi' >> /home/user/bin/nix-status && \
    echo 'echo ""' >> /home/user/bin/nix-status && \
    echo '' >> /home/user/bin/nix-status && \
    echo 'echo "=== Nix Store Usage ==="' >> /home/user/bin/nix-status && \
    echo 'df -h /nix 2>/dev/null || echo "❌ /nix not mounted"' >> /home/user/bin/nix-status && \
    echo 'echo ""' >> /home/user/bin/nix-status && \
    echo '' >> /home/user/bin/nix-status && \
    echo 'echo "=== Cargo Cache Usage ==="' >> /home/user/bin/nix-status && \
    echo 'du -sh ~/.cargo 2>/dev/null || echo "Empty or not mounted"' >> /home/user/bin/nix-status && \
    echo 'echo ""' >> /home/user/bin/nix-status && \
    echo '' >> /home/user/bin/nix-status && \
    echo 'echo "=== Rustup Cache Usage ==="' >> /home/user/bin/nix-status && \
    echo 'du -sh ~/.rustup 2>/dev/null || echo "Empty or not mounted"' >> /home/user/bin/nix-status && \
    echo 'echo ""' >> /home/user/bin/nix-status && \
    echo '' >> /home/user/bin/nix-status && \
    echo 'echo "=== Duplicate nixpkgs Check ==="' >> /home/user/bin/nix-status && \
    echo 'NIXPKGS_COUNT=$(ls -1d /nix/store/*-nixpkgs 2>/dev/null | wc -l)' >> /home/user/bin/nix-status && \
    echo 'echo "Found $NIXPKGS_COUNT nixpkgs directories (should be 1-2)"' >> /home/user/bin/nix-status && \
    echo 'if [ "$NIXPKGS_COUNT" -gt 3 ]; then' >> /home/user/bin/nix-status && \
    echo '    echo "⚠️  WARNING: Multiple nixpkgs found! Run '"'"'nix-clean'"'"' to clean up"' >> /home/user/bin/nix-status && \
    echo 'fi' >> /home/user/bin/nix-status && \
    echo 'echo ""' >> /home/user/bin/nix-status && \
    echo '' >> /home/user/bin/nix-status && \
    echo 'echo "=== Nix Version ==="' >> /home/user/bin/nix-status && \
    echo 'if [ -f "$HOME/.nix-profile/bin/nix" ]; then' >> /home/user/bin/nix-status && \
    echo '    nix --version' >> /home/user/bin/nix-status && \
    echo 'else' >> /home/user/bin/nix-status && \
    echo '    echo "❌ Nix not installed"' >> /home/user/bin/nix-status && \
    echo 'fi' >> /home/user/bin/nix-status && \
    echo 'echo ""' >> /home/user/bin/nix-status && \
    echo '' >> /home/user/bin/nix-status && \
    echo 'echo "=== Rust Version ==="' >> /home/user/bin/nix-status && \
    echo 'if command -v rustc &> /dev/null; then' >> /home/user/bin/nix-status && \
    echo '    rustc --version' >> /home/user/bin/nix-status && \
    echo '    cargo --version' >> /home/user/bin/nix-status && \
    echo 'else' >> /home/user/bin/nix-status && \
    echo '    echo "❌ Rust not installed"' >> /home/user/bin/nix-status && \
    echo 'fi' >> /home/user/bin/nix-status

RUN chmod +x /home/user/bin/nix-status

# Create nix-clean helper command
RUN echo '#!/bin/bash' > /home/user/bin/nix-clean && \
    echo '' >> /home/user/bin/nix-clean && \
    echo 'echo "🧹 Running Nix garbage collection..."' >> /home/user/bin/nix-clean && \
    echo 'echo ""' >> /home/user/bin/nix-clean && \
    echo '' >> /home/user/bin/nix-clean && \
    echo 'if [ -f "$HOME/.nix-profile/bin/nix-collect-garbage" ]; then' >> /home/user/bin/nix-clean && \
    echo '    echo "Before cleanup:"' >> /home/user/bin/nix-clean && \
    echo '    df -h /nix' >> /home/user/bin/nix-clean && \
    echo '    echo ""' >> /home/user/bin/nix-clean && \
    echo '' >> /home/user/bin/nix-clean && \
    echo '    nix-collect-garbage --delete-older-than 7d' >> /home/user/bin/nix-clean && \
    echo '    nix-store --optimise' >> /home/user/bin/nix-clean && \
    echo '' >> /home/user/bin/nix-clean && \
    echo '    echo ""' >> /home/user/bin/nix-clean && \
    echo '    echo "After cleanup:"' >> /home/user/bin/nix-clean && \
    echo '    df -h /nix' >> /home/user/bin/nix-clean && \
    echo '' >> /home/user/bin/nix-clean && \
    echo '    # Update last GC timestamp' >> /home/user/bin/nix-clean && \
    echo '    touch "$HOME/.nix-last-gc"' >> /home/user/bin/nix-clean && \
    echo 'else' >> /home/user/bin/nix-clean && \
    echo '    echo "❌ Nix not installed yet"' >> /home/user/bin/nix-clean && \
    echo '    exit 1' >> /home/user/bin/nix-clean && \
    echo 'fi' >> /home/user/bin/nix-clean

RUN chmod +x /home/user/bin/nix-clean

# Add to bashrc (appends to existing UDI bashrc)
RUN echo '' >> /home/user/.bashrc && \
    echo '# ============================================================================' >> /home/user/.bashrc && \
    echo '# Rust-Nix DevSpace Configuration' >> /home/user/.bashrc && \
    echo '# ============================================================================' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# PATH is now set by devfile.yaml env vars (works in all contexts)' >> /home/user/.bashrc && \
    echo '# This fallback ensures interactive shells still work if devfile PATH is missing' >> /home/user/.bashrc && \
    echo 'export PATH="/opt/holochain/bin:/home/user/bin:$PATH"' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# XDG directories are set by devfile.yaml env vars (injected by Kubernetes)' >> /home/user/.bashrc && \
    echo '# Just ensure they exist for interactive shells' >> /home/user/.bashrc && \
    echo 'mkdir -p "${XDG_CACHE_HOME:-/tmp/cache}" "${XDG_DATA_HOME:-/tmp/data}" "${XDG_STATE_HOME:-/tmp/state}" "${XDG_CONFIG_HOME:-/tmp/config}"' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# CRITICAL FIX: Recreate nix-profile symlink if missing (after workspace restart)' >> /home/user/.bashrc && \
    echo 'if [ -f /nix/.initialized-v4 ] && [ ! -L "$HOME/.nix-profile" ]; then' >> /home/user/.bashrc && \
    echo '    NIX_PROFILE="/nix/var/nix/profiles/per-user/$USER/profile"' >> /home/user/.bashrc && \
    echo '    if [ -e "$NIX_PROFILE" ]; then' >> /home/user/.bashrc && \
    echo '        echo "🔗 Recreating nix-profile symlink..."' >> /home/user/.bashrc && \
    echo '        ln -s "$NIX_PROFILE" "$HOME/.nix-profile"' >> /home/user/.bashrc && \
    echo '        echo "✅ Symlink recreated: ~/.nix-profile -> $NIX_PROFILE"' >> /home/user/.bashrc && \
    echo '    else' >> /home/user/.bashrc && \
    echo '        echo "⚠️  WARNING: Marker file exists but Nix profile not found"' >> /home/user/.bashrc && \
    echo '        echo "    Removing bad marker and re-initializing..."' >> /home/user/.bashrc && \
    echo '        rm -f /nix/.initialized-v4' >> /home/user/.bashrc && \
    echo '    fi' >> /home/user/.bashrc && \
    echo 'fi' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# Initialize Nix on first run (marker file missing)' >> /home/user/.bashrc && \
    echo 'if [ ! -f /nix/.initialized-v4 ] && [ -t 0 ]; then' >> /home/user/.bashrc && \
    echo '    echo "🔧 First run detected, initializing Nix and Rust..."' >> /home/user/.bashrc && \
    echo '    /home/user/bin/init-nix' >> /home/user/.bashrc && \
    echo 'fi' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# Source Nix profile if available' >> /home/user/.bashrc && \
    echo 'if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> /home/user/.bashrc && \
    echo '    source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> /home/user/.bashrc && \
    echo 'fi' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# Lightweight daily garbage collection (runs in background)' >> /home/user/.bashrc && \
    echo 'if [ -f "$HOME/.nix-profile/bin/nix-collect-garbage" ]; then' >> /home/user/.bashrc && \
    echo '    LAST_GC="$HOME/.nix-last-gc"' >> /home/user/.bashrc && \
    echo '    if [ ! -f "$LAST_GC" ] || [ -n "$(find "$LAST_GC" -mtime +1 2>/dev/null)" ]; then' >> /home/user/.bashrc && \
    echo '        (nix-collect-garbage --delete-older-than 7d > /dev/null 2>&1 && touch "$LAST_GC") &' >> /home/user/.bashrc && \
    echo '    fi' >> /home/user/.bashrc && \
    echo 'fi' >> /home/user/.bashrc && \
    echo '' >> /home/user/.bashrc && \
    echo '# Holochain helper: show scaffolding commands' >> /home/user/.bashrc && \
    echo 'holo-help() {' >> /home/user/.bashrc && \
    echo '    echo "Holochain CLI tools (pre-built binaries in /opt/holochain/bin):"' >> /home/user/.bashrc && \
    echo '    echo ""' >> /home/user/.bashrc && \
    echo '    echo "  holochain --version    # Conductor version"' >> /home/user/.bashrc && \
    echo '    echo "  hc --version           # CLI version"' >> /home/user/.bashrc && \
    echo '    echo "  lair-keystore --help   # Key management"' >> /home/user/.bashrc && \
    echo '    echo ""' >> /home/user/.bashrc && \
    echo '    echo "Scaffolding (P2P Shipyard / DarkSoil):"' >> /home/user/.bashrc && \
    echo '    echo "  hc scaffold            # Interactive scaffolding"' >> /home/user/.bashrc && \
    echo '    echo "  hc scaffold web-app    # Create new web app"' >> /home/user/.bashrc && \
    echo '    echo ""' >> /home/user/.bashrc && \
    echo '    echo "Templates: vanilla, lit, svelte, react, vue, headless"' >> /home/user/.bashrc && \
    echo '}' >> /home/user/.bashrc

# Fix permissions for arbitrary UID (Che requirement)
RUN chgrp -R 0 /home/user && \
    chmod -R g=u /home/user && \
    chmod -R g+w /home/user

USER 1000

WORKDIR /projects