#podman build -t harbor.ethosengine.com/devspaces/udi-plus:ubi9-plus-latest .
#podman push harbor.ethosengine.com/devspaces/udi-plus:ubi9-plus-latest

FROM quay.io/devfile/universal-developer-image:ubi9-latest

USER root

# Install Claude Code globally
RUN npm install -g @anthropic-ai/claude-code

# Install Java 21 via dnf
RUN dnf install -y java-21-openjdk-headless java-21-openjdk-devel && \
    dnf clean all

# Set Java 21 as the system default
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ENV PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH

# Verify Java 21 is working
RUN java -version && javac -version

# Install essential CLI tools for Claude Code context management
RUN dnf install -y \
    tree \
    htop \
    ncdu \
    fzf \
    tmux \
    screen \
    mc \
    && dnf clean all

# Create Claude + MCP dirs and make them user-writable
RUN mkdir -p /home/user/.claude \
           /home/user/.cache/sonarqube-mcp \
           /opt/mcp \
 && chown -R user:user /home/user/.claude /home/user/.cache /opt/mcp \
 && chmod -R 775 /home/user/.claude /home/user/.cache /opt/mcp

# Download SonarQube MCP JAR
ARG SONAR_MCP_VERSION=0.0.6.225
RUN curl -fL -o /opt/mcp/sonarqube-mcp.jar \
  "https://github.com/SonarSource/sonarqube-mcp-server/releases/download/${SONAR_MCP_VERSION}/sonarqube-mcp-server-${SONAR_MCP_VERSION}.jar"

USER user

# Set user environment for Java 21
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ENV PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH

# Verify installation
RUN claude --version || echo "Claude installed but needs auth"
RUN java -version