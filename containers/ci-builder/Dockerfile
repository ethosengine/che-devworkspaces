#podman build --pull --no-cache -t harbor.ethosengine.com/ethosengine/ci-builder:latest .
#podman push harbor.ethosengine.com/ethosengine/ci-builder:latest

# Multi-tool CI Builder Image
FROM node:18-alpine

# Install system dependencies
RUN apk add --no-cache \
    docker \
    docker-cli \
    docker-compose \
    kubectl \
    curl \
    wget \
    git \
    bash \
    unzip \
    sed \
    coreutils \
    ca-certificates \
    openssh-client \
    openjdk17-jre \
    chromium \
    chromium-chromedriver

# Set Chrome environment for headless testing with sandbox disabled for root
ENV CHROME_BIN=/usr/bin/chromium-browser \
    CHROME_PATH=/usr/bin/chromium-browser \
    CHROME_DEVEL_SANDBOX=/usr/lib/chromium/chrome-sandbox

# Create chromium wrapper script that adds --no-sandbox flag
RUN echo '#!/bin/sh' > /usr/bin/chromium-browser-wrapper && \
    echo 'exec /usr/bin/chromium-browser --no-sandbox --disable-dev-shm-usage --disable-gpu "$@"' >> /usr/bin/chromium-browser-wrapper && \
    chmod +x /usr/bin/chromium-browser-wrapper

# Update Chrome environment to use wrapper
ENV CHROME_BIN=/usr/bin/chromium-browser-wrapper \
    CHROME_PATH=/usr/bin/chromium-browser-wrapper

# Set JAVA_HOME for system JRE
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Install SonarQube Scanner and configure to use system JRE
RUN curl -o sonar-scanner.zip -L https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-6.2.1.4610-linux-x64.zip && \
    unzip sonar-scanner.zip && \
    mv sonar-scanner-6.2.1.4610-linux-x64 /opt/sonar-scanner && \
    rm sonar-scanner.zip && \
    # Replace glibc Java with Alpine-compatible system Java
    rm -f /opt/sonar-scanner/jre/bin/java && \
    ln -s /usr/bin/java /opt/sonar-scanner/jre/bin/java && \
    ln -s /opt/sonar-scanner/bin/sonar-scanner /usr/local/bin/sonar-scanner

# Add Docker socket mount point
VOLUME ["/var/run/docker.sock"]

# Set working directory
WORKDIR /workspace

# Keep container running
CMD ["tail", "-f", "/dev/null"]