# Start from the universal developer image
FROM quay.io/devfile/universal-developer-image:ubi9-latest

USER root
RUN mkdir -m 0755 /nix && chown 10001:10001 /nix

USER 10001

# Add Rust to PATH
RUN echo 'export PATH="/home/tooling/.cargo/bin:$PATH"' >> ~/.bashrc

# Try to install Nix during build
RUN curl -L https://nixos.org/nix/install | sh -s -- --no-daemon || echo "Initial Nix install completed"

# Add simple Nix setup using echo commands
RUN echo '' >> ~/.bashrc \
    && echo '# Nix setup with fallback installation' >> ~/.bashrc \
    && echo 'if [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ]; then' >> ~/.bashrc \
    && echo '    source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> ~/.bashrc \
    && echo 'elif ! command -v nix >/dev/null 2>&1 && [ ! -f "$HOME/.nix-install-attempted" ]; then' >> ~/.bashrc \
    && echo '    echo "Installing Nix..."' >> ~/.bashrc \
    && echo '    curl -sL https://nixos.org/nix/install | sh -s -- --no-daemon' >> ~/.bashrc \
    && echo '    touch "$HOME/.nix-install-attempted"' >> ~/.bashrc \
    && echo '    [ -f "$HOME/.nix-profile/etc/profile.d/nix.sh" ] && source "$HOME/.nix-profile/etc/profile.d/nix.sh"' >> ~/.bashrc \
    && echo 'fi' >> ~/.bashrc

WORKDIR /projects