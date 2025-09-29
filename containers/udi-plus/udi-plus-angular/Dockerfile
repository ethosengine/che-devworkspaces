#podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/udi-plus-angular:latest .
#podman push harbor.ethosengine.com/devspaces/udi-plus-angular:latest

# Test derived image approach - using udi-plus as base
FROM harbor.ethosengine.com/devspaces/udi-plus:latest

# Switch to root for Angular CLI installation
USER root

# Install Angular CLI on top of udi-plus base
RUN npm install -g @angular/cli

# Verify Angular CLI installation
RUN ng version

USER user

# Final verification of inherited tools + Angular CLI
RUN echo "=== Environment Check ===" && \
    echo "Node: $(node --version)" && \
    echo "NPM: $(npm --version)" && \
    echo "Angular CLI: $(ng version --skip-git 2>/dev/null | head -1)" && \
    echo "Java: $(java -version 2>&1 | head -1)" && \
    echo "Claude: $(claude --version 2>/dev/null || echo 'needs auth')" && \
    echo "Tree: $(tree --version 2>/dev/null || echo 'installed')" && \
    echo "=== All tools ready ==="