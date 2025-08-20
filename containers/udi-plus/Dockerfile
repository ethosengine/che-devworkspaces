#podman build -t harbor.ethosengine.com/devspaces/udi-plus:ubi9-plus-latest .
#podman push harbor.ethosengine.com/devspaces/udi-plus:ubi9-plus-latest

FROM quay.io/devfile/universal-developer-image:ubi9-latest

# Switch to root temporarily for installation
USER root

# Install Claude Code globally
RUN npm install -g @anthropic-ai/claude-code

# Install Java 21 via dnf (alongside the SDKMAN Java 17)
RUN dnf install -y java-21-openjdk-headless java-21-openjdk-devel && \
    dnf clean all

# Set Java 21 as the system default (this will override SDKMAN's Java in PATH)
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ENV PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH

# Verify Java 21 is working
RUN java -version


# Verify Java 21 is installed and working
RUN java -version && javac -version

# Create claude directory with proper permissions for the user
RUN mkdir -p /home/user/.claude && \
    chown -R user:user /home/user/.claude && \
    chmod 755 /home/user/.claude

# Switch back to user (UDI runs as user ID 1001)
USER user

# Set user environment for Java 21
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk
# Verify installation
RUN claude --version || echo "Claude installed but needs auth"