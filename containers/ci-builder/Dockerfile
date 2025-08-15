#podman build --pull --no-cache -t harbor.ethosengine.com/ethosengine/ci-builder:ci-builder-latest .
#podman push harbor.ethosengine.com/ethosengine/ci-builder:ci-builder-latest

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
    openjdk11-jre \
    chromium \
    chromium-chromedriver

# Set Chrome environment for headless testing
ENV CHROME_BIN=/usr/bin/chromium-browser \
    CHROME_PATH=/usr/bin/chromium-browser

# Install SonarQube Scanner
RUN curl -o sonar-scanner.zip -L https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-4.8.0.2856-linux.zip && \
    unzip sonar-scanner.zip && \
    mv sonar-scanner-4.8.0.2856-linux /opt/sonar-scanner && \
    ln -s /opt/sonar-scanner/bin/sonar-scanner /usr/local/bin/sonar-scanner && \
    rm sonar-scanner.zip

# Add Docker socket mount point
VOLUME ["/var/run/docker.sock"]

# Set working directory
WORKDIR /workspace

# Keep container running
CMD ["tail", "-f", "/dev/null"]