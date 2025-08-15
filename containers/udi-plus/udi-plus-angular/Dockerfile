#podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/udi-plus-angular:ubi9-plus-angular-latest .
#podman push harbor.ethosengine.com/devspaces/udi-plus-angular:ubi9-plus-angular-latest

FROM quay.io/devfile/universal-developer-image:ubi9-latest

# Switch to root temporarily for installation
USER root

# Install Claude Code globally
RUN npm install -g @anthropic-ai/claude-code
RUN npm install -g @angular/cli 

# Create claude directory with proper permissions for the user
RUN mkdir -p /home/user/.claude && \
    chown -R user:user /home/user/.claude && \
    chmod 755 /home/user/.claude

# Switch back to user (UDI runs as user ID 1001)
USER user

# Verify installation
RUN claude --version || echo "Claude installed but needs auth"