# Build and push commands:
# podman build -t harbor.ethosengine.com/devspaces/rust-nix-dev:latest .
# podman push harbor.ethosengine.com/devspaces/rust-nix-dev:latest

FROM harbor.ethosengine.com/devspaces/udi-plus:latest

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

# Create Nix store directory
RUN mkdir -p /nix && \
    chown user:user /nix

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
echo "🔧 Initializing Holochain development environment..."\n\
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
    # Test Holochain scaffolding tool\n\
    if nix run --refresh -j0 -v "github:holochain/holonix?ref=main-0.5#hc-scaffold" -- --version >/dev/null 2>&1; then\n\
        echo "✅ Holochain development environment ready!"\n\
        exit 0\n\
    fi\n\
fi\n\
\n\
echo "📦 Installing Holochain development environment..."\n\
\n\
# Clean up any broken profiles\n\
rm -rf ~/.nix-profile ~/.nix-defexpr ~/.nix-channels ~/.local/state/nix 2>/dev/null || true\n\
\n\
# Install using official Holochain setup script\n\
bash <(curl https://holochain.github.io/holochain/setup.sh)\n\
\n\
# Source the profile\n\
source "$HOME/.nix-profile/etc/profile.d/nix.sh"\n\
\n\
echo "🧪 Verifying Holochain setup..."\n\
nix run --refresh -j0 -v "github:holochain/holonix?ref=main-0.5#hc-scaffold" -- --version\n\
\n\
echo "✅ Holochain development environment installation complete!"\n\
echo "🚀 Ready to scaffold Holochain applications!"\n' \
    > /home/user/bin/init-holochain && chmod +x /home/user/bin/init-holochain

# Create nix wrapper that ensures initialization
RUN printf '#!/bin/bash\n\
if [ ! -f "$HOME/.nix-profile/bin/nix" ]; then\n\
    echo "🔧 Nix not found, running initialization..."\n\
    /home/user/bin/init-holochain\n\
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
# Holochain Development Environment Setup\n\
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
# Auto-initialize Holochain environment on first shell\n\
if [ ! -f "$HOME/.nix-profile/bin/nix" ] && [ -t 0 ]; then\n\
    echo "🔧 First run detected, initializing Holochain development environment..."\n\
    /home/user/bin/init-holochain\n\
fi\n\
\n\
# Source Nix profile if available\n\
if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then\n\
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"\n\
fi\n\
\n\
# Holochain helper functions\n\
holo-scaffold() {\n\
    echo "🚀 Scaffolding new Holochain application..."\n\
    nix run --refresh -j0 -v "github:holochain/holonix?ref=main-0.5#hc-scaffold" -- "$@"\n\
}\n\
\n\
holo-new-project() {\n\
    cd /projects\n\
    echo "📍 Creating new project in /projects"\n\
    echo "Available templates: web-app, vanilla, lit, svelte, react, vue, headless"\n\
    echo "Usage: holo-scaffold web-app my-forum-app"\n\
}\n\
\n\
if [ -f "$HOME/.nix-profile/bin/nix" ]; then\n\
    echo "🚀 Holochain Development Environment Ready!"\n\
    echo "   Run '\''holo-new-project'\'' to get started"\n\
    echo "   Run '\''holo-scaffold web-app my-app'\'' to create a new hApp"\n\
fi\n' >> /home/user/.bashrc

WORKDIR /projects