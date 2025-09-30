# Build and push commands:
# podman build -t harbor.ethosengine.com/devspaces/rust-nix-dev:latest .
# podman push harbor.ethosengine.com/devspaces/rust-nix-dev:latest

ARG BASE_TAG=latest
FROM harbor.ethosengine.com/devspaces/udi-plus:${BASE_TAG}

# Install system dependencies and tools
USER root
RUN dnf install -y glibc-langpack-en glibc-locale-source && \
    localedef -i en_US -f UTF-8 en_US.UTF-8 && \
    dnf clean all

# Install packages needed for Holochain dev tools (hc spin, hc launch)
RUN dnf install -y \
    nss \
    atk \
    at-spi2-atk \
    cups-libs \
    gtk3 \
    && dnf clean all

# Create Nix store and config directories
RUN mkdir -p /nix /etc/nix && \
    chown user:user /nix

# Configure Nix settings (system-level for better performance)
RUN printf 'experimental-features = nix-command flakes\n\
accept-flake-config = true\n\
substituters = https://cache.nixos.org https://holochain-ci.cachix.org https://darksoil-studio.cachix.org\n\
trusted-public-keys = cache.nixos.org-1:6NCHdD59X431o0gWypbMrAURkbJ16ZPMQFGspcDShjY= holochain-ci.cachix.org-1:5IUSkZc0aoRS53rfkvH9Kid40NpyjwCMCzwRTXy+QN8= darksoil-studio.cachix.org-1:UEi+aujy44s41XL/pscLw37KEVpTEIn8N/kn7jO8rkc=\n\
sandbox = false\n\
system-features = recursive-nix\n\
filter-syscalls = false\n' \
    > /etc/nix/nix.conf

# Create XDG directories that will be volume-mounted
RUN mkdir -p /nix-xdg/{cache,data,state,config} && \
    chmod -R 777 /nix-xdg

# Switch to user (matching devfile user ID)
USER user
ENV USER=user HOME=/home/user

# Create initialization script that runs on workspace startup
RUN mkdir -p /home/user/bin && \
    printf '#!/bin/bash\n\
set -e\n\
\n\
echo "🔧 Initializing Nix environment..."\n\
\n\
# Set up environment\n\
export XDG_CACHE_HOME=/nix-xdg/cache\n\
export XDG_DATA_HOME=/nix-xdg/data\n\
export XDG_STATE_HOME=/nix-xdg/state\n\
export XDG_CONFIG_HOME=/nix-xdg/config\n\
export LANG=en_US.UTF-8\n\
export LC_ALL=en_US.UTF-8\n\
\n\
# Configure NPM to use workspace-persistent cache\n\
export NPM_CONFIG_CACHE=/nix-xdg/cache/npm\n\
mkdir -p /nix-xdg/cache/npm\n\
\n\
# Check if Nix is already installed and working\n\
if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ] && [ -d "$HOME/.nix-profile/bin" ]; then\n\
    echo "✅ Nix already installed and working"\n\
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"\n\
    exit 0\n\
fi\n\
\n\
echo "📦 Installing Nix..."\n\
\n\
# Clean up any broken profiles\n\
rm -rf ~/.nix-profile ~/.nix-defexpr ~/.nix-channels ~/.local/state/nix 2>/dev/null || true\n\
\n\
# Install Nix\n\
curl -L https://nixos.org/nix/install | sh -s -- --no-daemon\n\
\n\
# Source the profile\n\
source "$HOME/.nix-profile/etc/profile.d/nix.sh"\n\
\n\
# Configure channels\n\
nix-channel --add https://nixos.org/channels/nixpkgs-unstable nixpkgs\n\
nix-channel --update\n\
\n\
echo "✅ Nix installation complete!"\n\
echo "🚀 Run '\''source ~/.nix-profile/etc/profile.d/nix.sh'\'' to activate"\n' \
    > /home/user/bin/init-nix && chmod +x /home/user/bin/init-nix

# Create nix wrapper that ensures initialization
RUN printf '#!/bin/bash\n\
if [ ! -f "$HOME/.nix-profile/bin/nix" ]; then\n\
    echo "🔧 Nix not found, running initialization..."\n\
    /home/user/bin/init-nix\n\
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"\n\
fi\n\
\n\
export XDG_CACHE_HOME=/nix-xdg/cache\n\
export XDG_DATA_HOME=/nix-xdg/data\n\
export XDG_STATE_HOME=/nix-xdg/state\n\
export XDG_CONFIG_HOME=/nix-xdg/config\n\
export LANG=en_US.UTF-8\n\
export LC_ALL=en_US.UTF-8\n\
export NPM_CONFIG_CACHE=/nix-xdg/cache/npm\n\
\n\
exec "$HOME/.nix-profile/bin/nix" "$@"\n' \
    > /home/user/bin/nix && chmod +x /home/user/bin/nix


# Update bashrc for automatic setup
RUN printf '\n\
# Nix Environment Setup\n\
export XDG_CACHE_HOME=/nix-xdg/cache\n\
export XDG_DATA_HOME=/nix-xdg/data\n\
export XDG_STATE_HOME=/nix-xdg/state\n\
export XDG_CONFIG_HOME=/nix-xdg/config\n\
export LANG=en_US.UTF-8\n\
export LC_ALL=en_US.UTF-8\n\
export NPM_CONFIG_CACHE=/nix-xdg/cache/npm\n\
\n\
# Add user bin to PATH\n\
export PATH=/home/user/bin:$PATH\n\
\n\
# Auto-initialize Nix on first shell\n\
if [ ! -f "$HOME/.nix-profile/bin/nix" ] && [ -t 0 ]; then\n\
    echo "🔧 First run detected, initializing Nix..."\n\
    /home/user/bin/init-nix\n\
fi\n\
\n\
# Source Nix profile if available\n\
if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then\n\
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"\n\
fi\n\
\n\
# Holochain helper functions\n\
holo-scaffold() {\n\
    nix run "github:holochain/holonix?ref=main-0.5#hc-scaffold" -- "$@"\n\
}\n\
\n\
holo-new-project() {\n\
    echo "Available templates: web-app, vanilla, lit, svelte, react, vue, headless"\n\
    echo "Usage: holo-scaffold web-app my-app-name"\n\
}\n\
\n' >> /home/user/.bashrc

WORKDIR /projects