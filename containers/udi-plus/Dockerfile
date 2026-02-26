#podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/udi-plus:latest .
#podman push harbor.ethosengine.com/devspaces/udi-plus:latest

# UBI10 provides GCC 14.2 with GLIBCXX_3.4.32, needed for pre-built Holochain binaries (require 3.4.30)
# RHEL 10 released May 2025, based on Fedora 40, supported until 2035
# See: https://lwn.net/Articles/1021827/
#
# NOTE: Using base-developer-image (not universal-developer-image) because ubi10-latest
# only exists for base. The base image (~800MB) lacks languages/tools that universal has (~8.75GB):
#   - Languages: Java, Node.js, Python, Go, Rust, .NET, PHP, C/C++, Scala
#   - Cloud tools: kubectl, helm, terraform, oc, docker-compose
#   - Build tools: Maven, Gradle
# We install what we need below.
FROM quay.io/devfile/base-developer-image:ubi10-latest

USER root

# Install Node.js (required for npm-based tools like gemini-cli)
# Using NodeSource for latest LTS
RUN dnf install -y nodejs npm && dnf clean all

# Install Gemini CLI globally via npm
RUN npm install -g @google/gemini-cli

# Install Claude Code via npm (native installer has issues in container builds)
RUN npm install -g @anthropic-ai/claude-code

# Install Java 21 via dnf
RUN dnf install -y java-21-openjdk-headless java-21-openjdk-devel && \
    dnf clean all

# Set Java 21 as the system default for mpc plugin support
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ENV PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH

# Verify Java 21 is working
RUN java -version && javac -version

# Install essential CLI tools for Claude Code context management
RUN dnf install -y \
    ncdu \
    fzf \
    screen \
    && dnf clean all

# Install Chrome for Testing (Google's official distribution for automated testing)
# Provides a stable, headless-capable Chrome binary for npm/Karma tests
# Note: wget is already in base image, just need unzip temporarily
RUN dnf install -y unzip && \
    wget -q https://storage.googleapis.com/chrome-for-testing-public/131.0.6778.87/linux64/chrome-linux64.zip && \
    unzip -q chrome-linux64.zip -d /opt/ && \
    rm chrome-linux64.zip && \
    ln -s /opt/chrome-linux64/chrome /usr/local/bin/chrome && \
    ln -s /opt/chrome-linux64/chrome /usr/local/bin/google-chrome && \
    dnf clean all

# Install Chrome runtime dependencies for graphics rendering
RUN dnf install -y \
    libdrm \
    mesa-libgbm \
    && dnf clean all

# Set Chrome binary path for Karma and other testing tools
ENV CHROME_BIN=/usr/local/bin/chrome
ENV CHROME_PATH=/opt/chrome-linux64/chrome

# Install kubeconform for Kubernetes manifest validation
ARG KUBECONFORM_VERSION=v0.7.0
RUN curl -sL "https://github.com/yannh/kubeconform/releases/download/${KUBECONFORM_VERSION}/kubeconform-linux-amd64.tar.gz" | tar xz -C /usr/local/bin && \
    kubeconform -v

# Install nerdctl + buildctl for local container image builds via BuildKit sidecar
# Versions match ci-builder image for consistency
ARG NERDCTL_VERSION=1.7.7
ARG BUILDKIT_VERSION=0.12.5
RUN curl -fsSL "https://github.com/containerd/nerdctl/releases/download/v${NERDCTL_VERSION}/nerdctl-${NERDCTL_VERSION}-linux-amd64.tar.gz" | \
    tar -xz -C /usr/local/bin/ nerdctl && \
    curl -fsSL "https://github.com/moby/buildkit/releases/download/v${BUILDKIT_VERSION}/buildkit-v${BUILDKIT_VERSION}.linux-amd64.tar.gz" | \
    tar -xz -C /tmp/ && \
    mv /tmp/bin/buildctl /usr/local/bin/ && \
    rm -rf /tmp/bin

# Create MCP dirs and make them user-writable
# NOTE: Do NOT create /home/user/.claude here - it must be a symlink to $CLAUDE_CONFIG_DIR
# for the VS Code extension IDE detection to work. The devfile postStart creates this symlink.
# Creating a directory here causes permission issues with arbitrary UIDs in Eclipse Che.
# The Claude binary is installed to /usr/local/bin (not ~/.claude/bin) for the same reason.
RUN mkdir -p /home/user/.cache/sonarqube-mcp \
           /opt/mcp \
 && chown -R user:root /home/user/.cache /opt/mcp \
 && chmod -R g=u /home/user/.cache /opt/mcp

# Download SonarQube MCP JAR
# See: https://github.com/SonarSource/sonarqube-mcp-server/releases
# Docs: https://docs.sonarsource.com/sonarqube-mcp-server/build-and-configure/environment-variables
ARG SONAR_MCP_VERSION=1.5.0.1706
RUN curl -fL -o /opt/mcp/sonarqube-mcp.jar \
  "https://github.com/SonarSource/sonarqube-mcp-server/releases/download/${SONAR_MCP_VERSION}/sonarqube-mcp-server-${SONAR_MCP_VERSION}.jar"

USER user

# Set user environment for Java 21
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ENV PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH

# NOTE: VS Code CLI and Claude extension setup (must be done in devfile postStart)
# 1. VS Code CLI: checode only ships ubi8/ubi9 binaries. The ubi9 binary at
#    /checode/checode-linux-libc/ubi9/bin/remote-cli/code-oss works on ubi10 (forward compatible).
#    Symlink: /home/user/.local/bin/code -> code-oss
#
# 2. Claude Code persistence (CLAUDE_CONFIG_DIR has known issues):
#    - VS Code extension hardcodes ~/.claude/ide/ for IPC sockets
#    - CLI uses $CLAUDE_CONFIG_DIR/ide/ for IPC socket detection
#    - ~/.claude.json is hardcoded to home directory (ignores CLAUDE_CONFIG_DIR)
#    See: https://github.com/anthropics/claude-code/issues/4739
#    See: https://github.com/anthropics/claude-code/issues/3833
#
#    Solution: Symlinks created in devfile postStart:
#    - ~/.claude -> $CLAUDE_CONFIG_DIR (directory)
#    - ~/.claude.json -> $CLAUDE_CONFIG_DIR/.claude.json (hardcoded file)
#
# Both symlinks must be created at runtime via devfile postStart because:
#   - /home can be overwritten by PVC mounts at workspace startup
#   - $CLAUDE_CONFIG_DIR value is only known at runtime
#
# 3. Claude is installed globally via npm, so the binary is in the npm global bin path.
#    The postStart symlinks ~/.claude for config persistence.

# Verify installation
RUN claude --version
RUN java -version