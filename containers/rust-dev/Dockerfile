
# podman login harbor.ethosengine.com
# podman build -t rust-nix-dev .
# podman tag rust-nix-dev harbor.ethosengine.com/devspaces/rust-nix-dev:latest
# podman build -t harbor.ethosengine.com/devspaces/rust-nix-dev:latest .
# podman push harbor.ethosengine.com/devspaces/rust-nix-dev:latest

FROM quay.io/devfile/universal-developer-image:ubi9-latest

# 1) Install prerequisites for Nix and locale support
USER root
RUN dnf install -y glibc-langpack-en && dnf clean all

# 2) Create the Nix store directories
RUN mkdir -p /nix && chown 10001:10001 /nix

# 3) Pre-configure global Nix settings with caches already trusted
RUN mkdir -p /etc/nix && \
    printf 'experimental-features = nix-command flakes\n\
substituters = https://cache.nixos.org https://holochain-ci.cachix.org https://darksoil-studio.cachix.org\n\
trusted-public-keys = cache.nixos.org-1:6NCHdD59X431o0gWypbMrAURkbJ16ZPMQFGspcDShjY= holochain-ci.cachix.org-1:5IUSkZc0aoRS53rfkvH9Kid40NpyjwCMCzwRTXy+QN8= darksoil-studio.cachix.org-1:UEi+aujy44s41XL/pscLw37KEVpTEIn8N/kn7jO8rkc=\n' \
    > /etc/nix/nix.conf

# 4) Create writable directories in /tmp for all Nix/XDG needs
RUN mkdir -p /tmp/nix-cache /tmp/nix-data /tmp/nix-state /tmp/nix-config && \
    chmod -R 777 /tmp/nix-cache /tmp/nix-data /tmp/nix-state /tmp/nix-config

# 5) Set system-wide environment variables that Che can't override
RUN echo 'export XDG_CACHE_HOME=/tmp/nix-cache' >> /etc/profile && \
    echo 'export XDG_DATA_HOME=/tmp/nix-data' >> /etc/profile && \
    echo 'export XDG_STATE_HOME=/tmp/nix-state' >> /etc/profile && \
    echo 'export XDG_CONFIG_HOME=/tmp/nix-config' >> /etc/profile && \
    echo 'export LANG=en_US.UTF-8' >> /etc/profile && \
    echo 'export LC_ALL=en_US.UTF-8' >> /etc/profile

# 6) Switch to Che user
USER 10001
ENV USER=user HOME=/home/user

# 7) Install Nix in single-user mode
RUN curl -L https://nixos.org/nix/install | sh -s -- --no-daemon

# 8) Set up environment
ENV PATH=/home/user/.nix-profile/bin:/nix/var/nix/profiles/default/bin:$PATH \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    XDG_CACHE_HOME=/tmp/nix-cache \
    XDG_DATA_HOME=/tmp/nix-data \
    XDG_STATE_HOME=/tmp/nix-state \
    XDG_CONFIG_HOME=/tmp/nix-config

# 9) Create a wrapper script for nix that ALWAYS sets the environment
RUN mkdir -p /home/user/bin && \
    printf '#!/bin/bash\n\
export XDG_CACHE_HOME=/tmp/nix-cache\n\
export XDG_DATA_HOME=/tmp/nix-data\n\
export XDG_STATE_HOME=/tmp/nix-state\n\
export XDG_CONFIG_HOME=/tmp/nix-config\n\
export LANG=en_US.UTF-8\n\
export LC_ALL=en_US.UTF-8\n\
\n\
# Remove any existing cache files that might be readonly\n\
if [ -f "/home/user/.cache/nix/fetcher-cache-v3.sqlite" ]; then\n\
    rm -f /home/user/.cache/nix/fetcher-cache-v3.sqlite* 2>/dev/null || true\n\
fi\n\
\n\
# Ensure symlink exists\n\
if [ ! -L "/home/user/.cache/nix" ]; then\n\
    rm -rf /home/user/.cache/nix 2>/dev/null || true\n\
    ln -sf /tmp/nix-cache /home/user/.cache/nix\n\
fi\n\
\n\
# Call the real nix\n\
exec /home/user/.nix-profile/bin/nix "$@"\n' > /home/user/bin/nix && \
    chmod +x /home/user/bin/nix

# 10) Add bin to PATH before nix
ENV PATH=/home/user/bin:$PATH

# 11) Configure bashrc
RUN printf '\n\
# Fix cache on shell start\n\
if [ -f "/home/user/.cache/nix/fetcher-cache-v3.sqlite" ]; then\n\
    rm -f /home/user/.cache/nix/fetcher-cache-v3.sqlite* 2>/dev/null || true\n\
fi\n\
if [ ! -L "/home/user/.cache/nix" ]; then\n\
    rm -rf /home/user/.cache/nix 2>/dev/null || true\n\
    ln -sf /tmp/nix-cache /home/user/.cache/nix\n\
fi\n\
\n\
# Source nix\n\
if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then\n\
    source "$HOME/.nix-profile/etc/profile.d/nix.sh"\n\
fi\n\
\n\
# Ensure PATH includes our wrapper\n\
export PATH=/home/user/bin:$PATH\n\
\n\
# Set XDG vars\n\
export XDG_CACHE_HOME=/tmp/nix-cache\n\
export XDG_DATA_HOME=/tmp/nix-data\n\
export XDG_STATE_HOME=/tmp/nix-state\n\
export XDG_CONFIG_HOME=/tmp/nix-config\n\
export LANG=en_US.UTF-8\n\
export LC_ALL=en_US.UTF-8\n\
\n\
# Holochain helper\n\
holo-scaffold() {\n\
    cd /tmp && mkdir -p holochain-apps && cd holochain-apps\n\
    echo "📍 Switched to /tmp/holochain-apps for scaffolding"\n\
    echo "Run: nix run github:darksoil-studio/scaffolding#hc-scaffold-happ -- web-app"\n\
}\n\
\n\
echo "🚀 Nix + Holochain Environment Ready!"\n\
echo "   Run '\''holo-scaffold'\'' to start scaffolding"\n' >> /home/user/.bashrc

# 12) Pre-configure cachix
RUN mkdir -p /tmp/nix-config/nix && \
    cp /etc/nix/nix.conf /tmp/nix-config/nix/nix.conf

WORKDIR /projects