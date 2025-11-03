# podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/rust-nix-dev:latest .
# podman push harbor.ethosengine.com/devspaces/rust-nix-dev:latest

# ============================================================================
# Rust + Nix Development Container for Eclipse Che/DevSpaces
# ============================================================================
#
# CRITICAL: This image is designed for Eclipse Che/DevSpaces where:
# - PVCs completely REPLACE mounted directories (e.g., /nix becomes empty)
# - Containers run with arbitrary UIDs (not necessarily 1000)
# - UDI-plus provides DevSpaces integration, Claude Code, Java 21, etc.
#
# Architecture:
# - BUILD TIME: Install system deps, scripts, cached installers
# - RUNTIME: Install Nix/Rust to /nix (on PVC) during first terminal
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

# Create init-nix script (runs at RUNTIME, not build time)
# This will be called by .bashrc on first terminal open
RUN mkdir -p /home/user/bin && \
    cat > /home/user/bin/init-nix << 'INIT_NIX_EOF'
#!/bin/bash
set -e

echo "🔧 Initializing Nix environment..."

# Set XDG directories to use /tmp (ephemeral, doesn't consume PVC)
export XDG_CACHE_HOME=${XDG_CACHE_HOME:-/tmp/nix-cache}
export XDG_DATA_HOME=${XDG_DATA_HOME:-/tmp/nix-data}
export XDG_STATE_HOME=${XDG_STATE_HOME:-/tmp/nix-state}
export XDG_CONFIG_HOME=${XDG_CONFIG_HOME:-/tmp/nix-config}
export NPM_CONFIG_CACHE=${XDG_CACHE_HOME}/npm

# Create directories
mkdir -p "$XDG_CACHE_HOME" "$XDG_DATA_HOME" "$XDG_STATE_HOME" "$XDG_CONFIG_HOME" "$NPM_CONFIG_CACHE"

# CRITICAL FIX: Marker file in /nix (persists in PVC across restarts)
# Using versioned marker to allow forced re-initialization if needed
NIX_INIT_MARKER="/nix/.initialized-v3"

# Prevent parallel initialization from multiple shells
NIX_INIT_LOCK="/nix/.init-lock"
if [ -f "$NIX_INIT_LOCK" ]; then
    echo "⏳ Another shell is initializing Nix, waiting..."
    for i in {1..30}; do
        sleep 2
        if [ -f "$NIX_INIT_MARKER" ]; then
            echo "✅ Initialization completed by other shell"
            if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then
                source "$HOME/.nix-profile/etc/profile.d/nix.sh"
            fi
            exit 0
        fi
    done
    echo "⚠️  Lock timeout, removing stale lock"
    rm -f "$NIX_INIT_LOCK"
fi

# Check if already initialized
if [ -f "$NIX_INIT_MARKER" ]; then
    echo "✅ Nix already initialized (marker: $NIX_INIT_MARKER)"
    if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then
        source "$HOME/.nix-profile/etc/profile.d/nix.sh"
    fi
    exit 0
fi

# Check if Nix is working (might be initialized without marker)
if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ] && [ -x "$HOME/.nix-profile/bin/nix-env" ]; then
    echo "✅ Nix already working, creating marker"
    touch "$NIX_INIT_MARKER" 2>/dev/null || true
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"
    exit 0
fi

# Create lock file
touch "$NIX_INIT_LOCK" 2>/dev/null || true

echo "📦 Installing Nix (first time only, this may take 2-3 minutes)..."

# Only clean up if truly corrupt
if [ -d "$HOME/.nix-profile" ] && [ ! -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then
    echo "⚠️  Cleaning up corrupted Nix profile..."
    rm -rf ~/.nix-profile ~/.nix-defexpr ~/.nix-channels ~/.local/state/nix 2>/dev/null || true
fi

# Install Nix using cached installer (single-user mode for DevSpaces)
# The /nix directory is on the PVC, so this persists across restarts
if [ -f /tmp/nix-installer.sh ]; then
    bash /tmp/nix-installer.sh --no-daemon
else
    curl -L https://nixos.org/nix/install | sh -s -- --no-daemon
fi

# Source Nix profile
source "$HOME/.nix-profile/etc/profile.d/nix.sh"

# Configure Nix for storage optimization
mkdir -p ~/.config/nix
cat > ~/.config/nix/nix.conf << 'NIX_CONF'
# Automatic garbage collection thresholds
min-free = 5368709120
max-free = 10737418240

# Storage optimization
auto-optimise-store = true

# Don't keep unnecessary outputs
keep-outputs = false
keep-derivations = false
NIX_CONF

# Add nixpkgs channel (check if already exists to prevent duplicates)
if ! nix-channel --list | grep -q '^nixpkgs'; then
    echo "📦 Adding nixpkgs channel..."
    nix-channel --add https://nixos.org/channels/nixpkgs-unstable nixpkgs
    nix-channel --update
else
    echo "✅ nixpkgs channel already configured"
fi

echo "🦀 Installing Rust toolchain via Nix..."
nix-env -iA nixpkgs.rustup nixpkgs.gcc nixpkgs.pkg-config nixpkgs.openssl

# Initialize rustup
rustup default stable
rustup component add rust-analyzer rustfmt clippy

# Create marker file (prevents re-initialization)
touch "$NIX_INIT_MARKER"

# Remove lock file
rm -f "$NIX_INIT_LOCK"

echo "✅ Nix and Rust installation complete!"
echo "📦 Nix profile will be auto-loaded in new shells"
echo ""
echo "💡 Helpful commands:"
echo "  nix-status        - Check storage and health"
echo "  nix-clean         - Run garbage collection"
echo "  cargo --version   - Verify Rust installation"
INIT_NIX_EOF

RUN chmod +x /home/user/bin/init-nix

# Create nix-status helper command
RUN cat > /home/user/bin/nix-status << 'NIX_STATUS_EOF'
#!/bin/bash

echo "=== Nix Initialization Status ==="
if [ -f /nix/.initialized-v3 ]; then
    echo "✅ Initialized (marker: /nix/.initialized-v3)"
else
    echo "❌ Not initialized"
fi
echo ""

echo "=== Nix Store Usage ==="
df -h /nix 2>/dev/null || echo "❌ /nix not mounted"
echo ""

echo "=== Cargo Cache Usage ==="
du -sh ~/.cargo 2>/dev/null || echo "Empty or not mounted"
echo ""

echo "=== Rustup Cache Usage ==="
du -sh ~/.rustup 2>/dev/null || echo "Empty or not mounted"
echo ""

echo "=== Duplicate nixpkgs Check ==="
NIXPKGS_COUNT=$(ls -1d /nix/store/*-nixpkgs 2>/dev/null | wc -l)
echo "Found $NIXPKGS_COUNT nixpkgs directories (should be 1-2)"
if [ "$NIXPKGS_COUNT" -gt 3 ]; then
    echo "⚠️  WARNING: Multiple nixpkgs found! Run 'nix-clean' to clean up"
fi
echo ""

echo "=== Nix Version ==="
if [ -f "$HOME/.nix-profile/bin/nix" ]; then
    nix --version
else
    echo "❌ Nix not installed"
fi
echo ""

echo "=== Rust Version ==="
if command -v rustc &> /dev/null; then
    rustc --version
    cargo --version
else
    echo "❌ Rust not installed"
fi
NIX_STATUS_EOF

RUN chmod +x /home/user/bin/nix-status

# Create nix-clean helper command
RUN cat > /home/user/bin/nix-clean << 'NIX_CLEAN_EOF'
#!/bin/bash

echo "🧹 Running Nix garbage collection..."
echo ""

if [ -f "$HOME/.nix-profile/bin/nix-collect-garbage" ]; then
    echo "Before cleanup:"
    df -h /nix
    echo ""

    nix-collect-garbage --delete-older-than 7d
    nix-store --optimise

    echo ""
    echo "After cleanup:"
    df -h /nix

    # Update last GC timestamp
    touch "$HOME/.nix-last-gc"
else
    echo "❌ Nix not installed yet"
    exit 1
fi
NIX_CLEAN_EOF

RUN chmod +x /home/user/bin/nix-clean

# Add to bashrc (appends to existing UDI bashrc)
RUN cat >> /home/user/.bashrc << 'BASHRC_EOF'

# ============================================================================
# Rust-Nix DevSpace Configuration
# ============================================================================

# Add user bin to PATH
export PATH=/home/user/bin:$PATH

# Set XDG directories to use /tmp (ephemeral, doesn't consume PVC)
export XDG_CACHE_HOME=${XDG_CACHE_HOME:-/tmp/nix-cache}
export XDG_DATA_HOME=${XDG_DATA_HOME:-/tmp/nix-data}
export XDG_STATE_HOME=${XDG_STATE_HOME:-/tmp/nix-state}
export XDG_CONFIG_HOME=${XDG_CONFIG_HOME:-/tmp/nix-config}
export NPM_CONFIG_CACHE=${XDG_CACHE_HOME}/npm

# Create XDG directories
mkdir -p "$XDG_CACHE_HOME" "$XDG_DATA_HOME" "$XDG_STATE_HOME" "$XDG_CONFIG_HOME" "$NPM_CONFIG_CACHE"

# CRITICAL FIX: Check marker file in /nix (persists in PVC)
# Only initialize on FIRST terminal of a NEW workspace
if [ ! -f /nix/.initialized-v3 ] && [ -t 0 ]; then
    echo "🔧 First run detected, initializing Nix and Rust..."
    /home/user/bin/init-nix
fi

# Source Nix profile if available
if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"
fi

# Lightweight daily garbage collection (runs in background)
if [ -f "$HOME/.nix-profile/bin/nix-collect-garbage" ]; then
    LAST_GC="$HOME/.nix-last-gc"
    if [ ! -f "$LAST_GC" ] || [ -n "$(find "$LAST_GC" -mtime +1 2>/dev/null)" ]; then
        (nix-collect-garbage --delete-older-than 7d > /dev/null 2>&1 && touch "$LAST_GC") &
    fi
fi

# Holochain helper functions
holo-scaffold() {
    nix run "github:holochain/holonix?ref=main-0.5#hc-scaffold" -- "$@"
}

holo-new-project() {
    echo "Available templates: web-app, vanilla, lit, svelte, react, vue, headless"
    echo "Usage: holo-scaffold web-app my-app-name"
}
BASHRC_EOF

# Fix permissions for arbitrary UID (Che requirement)
RUN chgrp -R 0 /home/user && \
    chmod -R g=u /home/user && \
    chmod -R g+w /home/user

USER 1000

WORKDIR /projects
