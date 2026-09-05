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
FROM harbor.ethosengine.com/proxy-quay/devfile/base-developer-image:ubi10-latest

USER root

# Install Node.js 24 (LTS "Krypton") from the official pinned tarball.
#
# NOT `dnf install nodejs`: UBI10's appstream tops out at nodejs 22.x and
# exposes no module stream to enable a newer one (verified 2026-07-30 against
# the live base — `dnf module list nodejs` finds nothing), so dnf can never
# reach the floor the elohim monorepo declares in its root package.json
# (engines.node ">=24.15"). Angular 22.1.0 itself wants
# ^22.22.3 || ^24.15.0 || >=26.0.0, and CI builds on node:24-bookworm
# (ci-builder), so pinning here puts devspaces and CI on the same major
# instead of leaving a skew that RHEL's packaging decisions control.
#
# The unpinned dnf install was also silently version-drifting: it sat at
# 22.22.2 — one patch BELOW Angular 22's floor — until 22.23.1 landed.
#
# Fetched from nodejs.org (no Nexus proxy exists for nodejs.org/dist, and
# Nexus is deliberately reserved for first-party components) behind a sha256
# gate, matching the pinned-binary pattern used for kubeconform/nerdctl below.
# Bumping: update BOTH args from https://nodejs.org/dist/v<X>/SHASUMS256.txt.
#
# .tar.GZ, not .tar.xz: this base has no xz binary, so `tar -xJ` dies with
# "tar (child): xz: Cannot exec" AFTER the checksum passes (#323). gzip is
# present — the kubeconform/nerdctl/buildctl steps below already rely on it.
ARG NODE_VERSION=24.18.1
ARG NODE_SHA256=9f5eb6ac21845a66c493c91a253b1da32fd684e89e9b7202d4936982336be4ca
RUN curl -fsSL -o /tmp/node.tar.gz \
        "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.gz" && \
    echo "${NODE_SHA256}  /tmp/node.tar.gz" | sha256sum -c - && \
    tar -xzf /tmp/node.tar.gz -C /usr/local --strip-components=1 \
        --exclude CHANGELOG.md --exclude LICENSE --exclude README.md && \
    rm -f /tmp/node.tar.gz && \
    test "v${NODE_VERSION}" = "$(node --version)" && \
    echo "node $(node --version) / npm $(npm --version)"

# Install mongod (MongoDB Community 8.0) from the official pinned tarball.
#
# Purpose: the local multi-peer mesh (elohim: app/elohim-app/scripts/hc-mesh.sh)
# runs a loopback mongod so BOTH local doorways boot archive-backed — the
# Mongo-side projection archive (app_file_cache / warm-shell ShellArchive /
# DoorwayResolver store) is what lets the boot-order self-heal family be proven
# at the desk instead of only in the fleet. Without a mongod the doorway
# constructs an inert warm-shell store, which is precisely the production shape
# elohim 18a65fd0d found un-wired for that cure's entire life (2026-08-21).
#
# rhel93 build on a UBI10 base: verified 2026-08-21 on the live image lineage
# (glibc 2.39, OpenSSL 3.5) — `mongod --fork` reaches "Waiting for connections".
# MongoDB publishes no UBI10/RHEL10 artifact yet; bump to one when it exists.
# Only the server binary is installed (no mongos/tools): ~150MB, bin only.
# Bumping: update BOTH args — sha256 from the tarball itself
# (`curl -fsSL <url> | sha256sum`); MongoDB publishes per-file .sha256 alongside.
ARG MONGOD_VERSION=8.0.12
ARG MONGOD_SHA256=d14e31970b7b32e010564122086c2c866cbd03e289625855d6e97103f5135fe9
RUN curl -fsSL -o /tmp/mongodb.tgz \
        "https://fastdl.mongodb.org/linux/mongodb-linux-x86_64-rhel93-${MONGOD_VERSION}.tgz" && \
    echo "${MONGOD_SHA256}  /tmp/mongodb.tgz" | sha256sum -c - && \
    tar -xzf /tmp/mongodb.tgz --wildcards --strip-components=2 -C /usr/local/bin '*/bin/mongod' && \
    rm -f /tmp/mongodb.tgz && \
    mongod --version | head -1

# Install Claude Code globally via npm (installs to /usr/local/bin/claude)
# Native installer puts binary in ~/.local/bin which gets wiped by PVC mounts in Che
RUN npm install -g @anthropic-ai/claude-code

# Install OpenAI Codex CLI globally via npm (installs to /usr/local/bin/codex)
# Same rationale as Claude Code above: the standalone installer (chatgpt.com/codex/install.sh)
# drops the binary in ~/.local/bin, which PVC mounts wipe in Che. npm-global survives.
RUN npm install -g @openai/codex

# Install Google Antigravity CLI as a system-wide native binary.
# Its official installer defaults to ~/.local/bin, which is shadowed by Che's
# home mounts. --dir keeps the binary in the immutable image while the devfile
# persists Antigravity's runtime state separately on /projects.
# The installer verifies the release payload against Google's SHA-512 manifest.
RUN curl -fsSL https://antigravity.google/cli/install.sh | \
    bash -s -- --dir /usr/local/bin

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
    wget -q https://nexus.ethosengine.com/repository/googleapis-proxy/chrome-for-testing-public/131.0.6778.87/linux64/chrome-linux64.zip && \
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
RUN curl -sL "https://nexus.ethosengine.com/repository/github-releases-proxy/yannh/kubeconform/releases/download/${KUBECONFORM_VERSION}/kubeconform-linux-amd64.tar.gz" | tar xz -C /usr/local/bin && \
    kubeconform -v

# Install hadolint for Dockerfile linting
ARG HADOLINT_VERSION=v2.12.0
RUN curl -sL -o /usr/local/bin/hadolint \
    "https://nexus.ethosengine.com/repository/github-releases-proxy/hadolint/hadolint/releases/download/${HADOLINT_VERSION}/hadolint-Linux-x86_64" && \
    chmod +x /usr/local/bin/hadolint

# Install GitHub CLI (gh) — pinned + checksummed like sccache/node.
# Bump: TAG=$(curl -fsSL https://api.github.com/repos/cli/cli/releases/latest | grep -m1 tag_name | sed -E 's/.*"v([^"]+)".*/\1/');
#       curl -fsSL "https://nexus.ethosengine.com/repository/github-releases-proxy/cli/cli/releases/download/v${TAG}/gh_${TAG}_checksums.txt" | grep linux_amd64.tar.gz
# Lives in the image (not ~/bin) because a workspace restart wipes ~/bin and the
# git credential path then has nothing that reads GH_TOKEN (2026-09-05).
ARG GH_VERSION=2.100.0
ARG GH_SHA256=e4d4bb4498e8d007abe545b6568926793ace1b6447da598294a610018cb164be
RUN curl -fsSL -o /tmp/gh.tar.gz \
      "https://nexus.ethosengine.com/repository/github-releases-proxy/cli/cli/releases/download/v${GH_VERSION}/gh_${GH_VERSION}_linux_amd64.tar.gz" \
 && echo "${GH_SHA256}  /tmp/gh.tar.gz" | sha256sum -c - \
 && tar -xzf /tmp/gh.tar.gz -C /tmp \
 && install -m 0755 "/tmp/gh_${GH_VERSION}_linux_amd64/bin/gh" /usr/local/bin/gh \
 && rm -rf /tmp/gh.tar.gz "/tmp/gh_${GH_VERSION}_linux_amd64" \
 && gh --version

# Install yamllint and check-jsonschema for YAML/devfile validation
# pip3 binary not available in UBI10 base; use python3 -m pip.
# pip routed through Nexus PyPI proxy to reduce upstream rate-limit exposure.
RUN python3 -m ensurepip && python3 -m pip install --no-cache-dir \
    --index-url https://nexus.ethosengine.com/repository/pypi-proxy/simple/ \
    yamllint check-jsonschema

# Install nerdctl + buildctl for local container image builds via BuildKit sidecar
# Versions match ci-builder image for consistency
ARG NERDCTL_VERSION=1.7.7
ARG BUILDKIT_VERSION=0.12.5
RUN curl -fsSL "https://nexus.ethosengine.com/repository/github-releases-proxy/containerd/nerdctl/releases/download/v${NERDCTL_VERSION}/nerdctl-${NERDCTL_VERSION}-linux-amd64.tar.gz" | \
    tar -xz -C /usr/local/bin/ nerdctl && \
    curl -fsSL "https://nexus.ethosengine.com/repository/github-releases-proxy/moby/buildkit/releases/download/v${BUILDKIT_VERSION}/buildkit-v${BUILDKIT_VERSION}.linux-amd64.tar.gz" | \
    tar -xz -C /tmp/ && \
    mv /tmp/bin/buildctl /usr/local/bin/ && \
    rm -rf /tmp/bin

# Create MCP dirs and make them user-writable
# NOTE: Do NOT create /home/user/.claude here - it must be a symlink to $CLAUDE_CONFIG_DIR
# for the VS Code extension IDE detection to work. The devfile postStart creates this symlink.
# Creating a directory here causes permission issues with arbitrary UIDs in Eclipse Che.
RUN mkdir -p /home/user/.cache/sonarqube-mcp \
           /opt/mcp \
 && chown -R user:root /home/user/.cache /opt/mcp \
 && chmod -R g=u /home/user/.cache /opt/mcp

# Download SonarQube MCP JAR
# See: https://github.com/SonarSource/sonarqube-mcp-server/releases
# Docs: https://docs.sonarsource.com/sonarqube-mcp-server/build-and-configure/environment-variables
ARG SONAR_MCP_VERSION=1.5.0.1706
RUN curl -fL -o /opt/mcp/sonarqube-mcp.jar \
  "https://nexus.ethosengine.com/repository/github-releases-proxy/SonarSource/sonarqube-mcp-server/releases/download/${SONAR_MCP_VERSION}/sonarqube-mcp-server-${SONAR_MCP_VERSION}.jar"

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
# 3. Claude Code is installed globally via npm, so the binary is in /usr/local/bin/.
#    The postStart symlinks ~/.claude for config persistence.

# Verify installation
RUN claude --version
RUN codex --version
RUN agy --version
RUN java -version
