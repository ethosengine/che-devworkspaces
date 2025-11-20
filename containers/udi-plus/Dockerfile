#podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/udi-plus:latest .
#podman push harbor.ethosengine.com/devspaces/udi-plus:latest

FROM quay.io/devfile/universal-developer-image:ubi9-latest

USER root

# Install Code Assistants globally
RUN npm install -g @anthropic-ai/claude-code
RUN npm install -g @google/gemini-cli


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
RUN dnf install -y wget unzip && \
    wget -q https://storage.googleapis.com/chrome-for-testing-public/131.0.6778.87/linux64/chrome-linux64.zip && \
    unzip -q chrome-linux64.zip -d /opt/ && \
    rm chrome-linux64.zip && \
    ln -s /opt/chrome-linux64/chrome /usr/local/bin/chrome && \
    ln -s /opt/chrome-linux64/chrome /usr/local/bin/google-chrome && \
    dnf remove -y wget unzip && \
    dnf clean all

# Install Chrome runtime dependencies for graphics rendering
RUN dnf install -y \
    libdrm \
    mesa-libgbm \
    && dnf clean all

# Set Chrome binary path for Karma and other testing tools
ENV CHROME_BIN=/usr/local/bin/chrome
ENV CHROME_PATH=/opt/chrome-linux64/chrome

# Create Claude + MCP dirs and make them user-writable
RUN mkdir -p /home/user/.claude \
           /home/user/.cache/sonarqube-mcp \
           /opt/mcp \
 && chown -R user:user /home/user/.claude /home/user/.cache /opt/mcp \
 && chmod -R 775 /home/user/.claude /home/user/.cache /opt/mcp

# Download SonarQube MCP JAR
ARG SONAR_MCP_VERSION=0.0.8.1353
RUN curl -fL -o /opt/mcp/sonarqube-mcp.jar \
  "https://github.com/SonarSource/sonarqube-mcp-server/releases/download/${SONAR_MCP_VERSION}/sonarqube-mcp-server-${SONAR_MCP_VERSION}.jar"

USER user

# Set user environment for Java 21
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ENV PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH

# NOTE: VS Code CLI symlink setup
# The base UBI image provides 'code-oss' at /checode/checode-linux-libc/ubi9/bin/remote-cli/code-oss
# However, many tools and documentation expect 'code' as the command name.
# Since /home is overwritten at Eclipse Che workspace startup, the symlink must be created
# via postStart commands in devfile.yaml files (not here in the Dockerfile).
# ⚠️ REVIEW REQUIRED: If the base quay.io/devfile/universal-developer-image:ubi9-latest changes,
# verify the code-oss path and update both this comment and the devfile postStart commands.

# Verify installation
RUN claude --version || echo "Claude installed but needs auth"
RUN java -version