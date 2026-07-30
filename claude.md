# Eclipse Che DevWorkspaces Guide

A practical reference for building secure, efficient DevWorkspaces in Eclipse Che and OpenShift Dev Spaces.

## This Repository

This repository (`che-devworkspaces`) provides custom container images and devfile configurations for Eclipse Che/OpenShift Dev Spaces. All images are built via Jenkins pipelines and hosted on Harbor registry.

### Image Architecture

The repository manages 5 container images in a dependency hierarchy:

**CI/CD Image (Independent):**
- **ci-builder**: Multi-tool CI/CD image (nerdctl, buildctl, kubectl, SonarQube scanner)
  - Registry: `harbor.ethosengine.com/ethosengine/ci-builder`
  - Dockerfile: `containers/ci-builder/Dockerfile`

**Development Images:**
```
quay.io/devfile/universal-developer-image:ubi9-latest
  └─> udi-plus (base with Claude Code CLI + Java 21)
       ├─> rust-nix-dev (Rust + Nix + Holochain)
       ├─> udi-plus-angular (Angular + Node.js)
       └─> udi-plus-gae (Google App Engine + Python 2.7) — ARCHIVED
```

- **udi-plus**: Base UDI with Claude Code pre-installed
  - Registry: `harbor.ethosengine.com/devspaces/udi-plus`
  - Triggers downstream builds automatically on successful build

- **rust-nix-dev**: Rust development with Nix package manager
  - Registry: `harbor.ethosengine.com/devspaces/rust-nix-dev`
  - Special: Nix/Rust installed at RUNTIME (not build time) to survive PVC mounts

- **udi-plus-angular**: Angular development
  - Registry: `harbor.ethosengine.com/devspaces/udi-plus-angular`

- **udi-plus-gae**: Google App Engine with Python 2.7 — **ARCHIVED**
  - Registry: `harbor.ethosengine.com/devspaces/udi-plus-gae`
  - Manual builds only; deliberately excluded from the udi-plus cascade

### Building Images

**Local builds:**
```bash
cd containers/udi-plus
podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/udi-plus:latest .
podman push harbor.ethosengine.com/devspaces/udi-plus:latest
```

**Jenkins pipelines:**
- Each image has its own pipeline (e.g., `devspaces-udi-plus`, `devspaces-rust-nix-dev`)
- Shared library: `jenkins/shared-library/vars/buildDevspaceImage.groovy`
- Each build creates 3 tags: `latest`, `<datestamp>`, `<git-hash>`
- Successful `udi-plus` builds trigger downstream image builds

See `jenkins/JENKINS_SETUP.md` for complete pipeline setup instructions.

### Persisting Data Across Workspace Restarts

**CRITICAL**: The `/projects` directory is the default persistent volume in Eclipse Che/DevSpaces:
- `/projects` persists across workspace restarts
- `/home/user` does NOT persist by default (unless explicitly mounted to a volume)
- Use `/projects` for any configuration that needs to survive restarts

**Example: Claude Code Configuration**
```yaml
env:
  - name: CLAUDE_CONFIG_DIR
    value: /projects/.claude-config
```

**⚠️ CLAUDE_CONFIG_DIR Known Issues:**
Claude Code's `CLAUDE_CONFIG_DIR` environment variable has [known bugs](https://github.com/anthropics/claude-code/issues/3833):
- `~/.claude.json` is hardcoded to home directory (ignores the variable)
- VS Code extension hardcodes `~/.claude/ide/` for IPC sockets
- CLI uses `$CLAUDE_CONFIG_DIR/ide/` for IPC socket detection (path mismatch)

**Our Workaround:** The devfile postStart creates symlinks to bridge these paths:
```bash
# Directory symlink - fixes IDE socket detection
ln -sf "$CLAUDE_CONFIG_DIR" /home/user/.claude

# File symlink - fixes hardcoded .claude.json location
ln -sf "$CLAUDE_CONFIG_DIR/.claude.json" /home/user/.claude.json
```

This ensures both the extension and CLI write/read from the same persistent location.

**Other common patterns:**
```yaml
# Store tool configurations in /projects
env:
  - name: KUBECONFIG
    value: /projects/.kube/config
  - name: DOCKER_CONFIG
    value: /projects/.docker
  - name: NPM_CONFIG_USERCONFIG
    value: /projects/.npmrc
```

### Directory Usage Constraints (Eclipse Che / Dev Containers)

**⚠️ CRITICAL FOR MCP SERVERS AND TOOLS**: When configuring any tool that stores data, be aware of these Eclipse Che constraints:

**Directories that DO NOT persist (ephemeral):**
- `/home/user/` - Overwritten on workspace restart
- `/home/user/.cache/` - Lost on restart
- `/home/user/.config/` - Lost on restart
- `/tmp/` - Cleared on restart
- Any directory not explicitly mounted to a PVC

**Directories that DO persist:**
- `/projects/` - Default PVC mount point
- `/projects/.config/` - Use for XDG_CONFIG_HOME
- `/projects/.cache/` - Use for XDG_CACHE_HOME
- `/projects/.claude-config/` - Claude Code settings
- Any directory explicitly mounted to a PVC via devfile

**MCP Server Storage Example (SonarQube):**
```yaml
# ❌ WRONG - /tmp is ephemeral, data lost on restart
export STORAGE_PATH=/tmp/sonarqube-mcp-storage

# ❌ WRONG - /home/user is overwritten on restart
export STORAGE_PATH=/home/user/.cache/sonarqube-mcp

# ✅ CORRECT - /projects persists across restarts
export STORAGE_PATH=/projects/.sonarqube-mcp
```

**Where to set environment variables:**
- **Dockerfile**: For build-time only or defaults that can be overridden
- **devfile.yaml**: For runtime environment variables (preferred for persistence paths)
- Devfile env vars override Dockerfile ENV directives

**Why this matters:**
1. MCP servers may cache authentication tokens, project data, or state
2. Build tool caches (Maven, npm, pip) benefit from persistence
3. IDE extensions may store settings in these directories
4. Losing data on restart wastes time and bandwidth re-downloading

## Platform Overview

- **Eclipse Che**: Open-source Kubernetes-native IDE platform
- **OpenShift Dev Spaces**: Enterprise product based on Eclipse Che
- **DevWorkspace Operator**: v0.19+ (Eclipse Che 7.42+) manages container lifecycle
- **Universal Developer Image (UDI)**: Pre-configured development container

## Security Fundamentals

### OpenShift Security Model
- Containers run with **arbitrary UIDs** (e.g., 1001110000)
- No `/etc/passwd` entries for these UIDs
- Use **fsGroup 0** for file permissions
- **container-build SCC** enables rootless builds

### Required Security Context

```yaml
spec:
  securityContext:
    runAsUser: 1001110000      # OpenShift assigns this
    runAsGroup: 0              # Root group for file access
    runAsNonRoot: true         # Required
    fsGroup: 0                 # Group ownership for volumes
  containers:
  - name: dev-tools
    securityContext:
      allowPrivilegeEscalation: true  # For container builds
      capabilities:
        add: ["SETGID", "SETUID"]     # For user switching
        drop: ["ALL"]                 # Drop all first
      readOnlyRootFilesystem: false   # Allow writes
```

## Decision Matrix: Dockerfile vs Devfile vs Runtime

### Use Dockerfile When:
- Installing **system packages** (PostgreSQL libs, compilers)
- Creating **team-standard base images**
- Optimizing **startup performance** with pre-built tools
- Requiring **complex build environments**

```dockerfile
FROM quay.io/devfile/universal-developer-image:latest

USER 0
RUN dnf -y install postgresql-devel && \
    dnf clean all && \
    npm install -g @angular/cli

# Use flexible UID
ARG USER_ID=10001
USER ${USER_ID}
```

### Use devfile.yaml When:
- Configuring **workspace resources** (CPU, memory, volumes)
- Setting **environment variables**
- Defining **development commands**
- Managing **multi-container** workspaces

```yaml
schemaVersion: 2.2.0
metadata:
  name: my-workspace
components:
- name: tools
  container:
    image: quay.io/devfile/universal-developer-image
    memoryLimit: 4Gi
    cpuLimit: 2000m
    env:
    - name: NODE_ENV
      value: development
```

### Use Runtime Commands When:
- Installing **project dependencies** (npm, pip, gem)
- Running **project-specific setup**
- Executing **build/test workflows**

```yaml
commands:
- id: install-deps
  exec:
    commandLine: |
      npm install
      pip install --user -r requirements.txt
    component: tools
```

## Volume Management

### Persistent Home Directory Pattern

```yaml
components:
- name: tools
  container:
    volumeMounts:
    - name: persistent-home
      path: /home/user
volumes:
- name: persistent-home
  size: 10Gi

# Preserve UDI environment
commands:
- id: restore-env
  exec:
    commandLine: |
      [ ! -f ~/.bashrc ] && cp /etc/skel/.bashrc ~/
      export PATH="/home/user/.local/bin:$PATH"
events:
  postStart: ["restore-env"]
```

### Storage Strategy

```yaml
# Per-workspace (production)
spec:
  devEnvironments:
    storage:
      pvcStrategy: "per-workspace"
      
# Volume organization
volumes:
- name: projects      # Source code
  size: 10Gi
- name: m2-cache      # Maven cache
  path: /home/user/.m2
  size: 5Gi
- name: npm-cache     # npm cache
  path: /home/user/.npm
  size: 2Gi
```

## Runtime Initialization Pattern (rust-nix-dev Example)

The `rust-nix-dev` image demonstrates a critical pattern for handling tools that must be installed to mounted volumes.

### The Problem
When you mount a PVC to `/nix`, Eclipse Che **completely replaces** the directory contents - anything installed at build time is lost. Traditional Dockerfile approaches fail:
```dockerfile
# ❌ This doesn't work - /nix gets replaced by empty PVC
RUN curl -L https://nixos.org/nix/install | sh
```

### The Solution: Two-Phase Initialization

**Phase 1: Build Time** (in Dockerfile)
- Install system dependencies (gcc, make, openssl-devel)
- Pre-download installers to `/tmp` (persists in image)
- Create initialization scripts in `/home/user/bin/`
- Configure `.bashrc` to run init script on first terminal

**Phase 2: Runtime** (first terminal open in workspace)
- Check for marker file `/nix/.initialized-v4` on the PVC
- If not found: install Nix to `/nix`, configure, install Rust, create marker
- If found: skip installation, just source Nix profile

### Implementation Details

**Dockerfile creates init script:**
```dockerfile
RUN echo '#!/bin/bash' > /home/user/bin/init-nix && \
    echo 'if [ ! -f /nix/.initialized-v4 ]; then' >> /home/user/bin/init-nix && \
    echo '  # Install Nix to /nix (on PVC)' >> /home/user/bin/init-nix && \
    echo '  bash /tmp/nix-installer.sh --no-daemon' >> /home/user/bin/init-nix && \
    echo '  touch /nix/.initialized-v4' >> /home/user/bin/init-nix && \
    echo 'fi' >> /home/user/bin/init-nix
```

**Bashrc auto-runs on first terminal:**
```dockerfile
RUN echo 'if [ ! -f /nix/.initialized-v4 ] && [ -t 0 ]; then' >> /home/user/.bashrc && \
    echo '  /home/user/bin/init-nix' >> /home/user/.bashrc && \
    echo 'fi' >> /home/user/.bashrc
```

**Devfile mounts PVC:**
```yaml
components:
  - name: rust-dev
    container:
      volumeMounts:
        - name: nix-store
          path: /nix
volumes:
  - name: nix-store
    size: 15Gi
```

### Key Points
- Marker file must be on the PVC (e.g., `/nix/.initialized-v4`)
- Pre-download installers to `/tmp` at build time for faster initialization
- Handle workspace restarts: symlinks in `/home/user` may need recreation
- Use versioned markers (`v4`) to allow forced re-initialization when needed
- Set `sandbox = false` in Nix config for rootless container compatibility

### Helper Commands Pattern
The rust-nix-dev image provides helper commands for users:
- `nix-status`: Check installation status and storage usage
- `nix-clean`: Run garbage collection manually

These are simple scripts in `/home/user/bin/` added to PATH.

## Environment Configuration

### Using ConfigMaps
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: workspace-env
  labels:
    controller.devfile.io/mount-to-devworkspace: "true"
    controller.devfile.io/watch-configmap: "true"
  annotations:
    controller.devfile.io/mount-as: env
data:
  API_URL: "http://api.example.com"
  NODE_ENV: "development"
```

### Using Secrets
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: workspace-secrets
  labels:
    controller.devfile.io/mount-to-devworkspace: "true"
  annotations:
    controller.devfile.io/mount-as: env
stringData:
  GITHUB_TOKEN: "your-token"
```

## Common Issues and Solutions

### Permission Denied
```yaml
# Problem: mkdir /home/user: permission denied
# Solution: Use alternative paths or init containers

components:
- name: fix-permissions
  container:
    image: busybox
    command: ['sh', '-c', 'chmod -R 775 /projects && chgrp -R 0 /projects']
    volumeMounts:
    - name: projects
      path: /projects
```

### Workspace Timeouts
```yaml
# Increase timeout
extraProperties:
  CHE_INFRA_KUBERNETES_WORKSPACE__START__TIMEOUT__MIN: "15"
  
# Enable image pre-pulling
kubectl patch checluster/eclipse-che --type='merge' \
  --patch '{"spec":{"components":{"imagePuller":{"enable":true}}}}'
```

### Memory Management
```yaml
components:
- name: java-app
  container:
    env:
    - name: JAVA_OPTS
      value: "-XX:MaxRAMPercentage=75.0"
    - name: NODE_OPTIONS  
      value: "--max-old-space-size=3072"
    memoryLimit: 4Gi
```

## Anti-Patterns to Avoid

### ❌ DON'T: Hardcode UIDs
```yaml
# Wrong
USER 1000

# Right
USER ${USER_ID:-10001}
```

### ❌ DON'T: Use sudo
```yaml
# Wrong
commandLine: "sudo apt-get install vim"

# Right - install in Dockerfile as USER 0
```

### ❌ DON'T: Store secrets in devfiles
```yaml
# Wrong
env:
- name: API_KEY
  value: "secret-123"

# Right - use Kubernetes secrets
```

### ❌ DON'T: Mix persistent and temporary data
```yaml
# Wrong - logs in persistent volume
volumeMounts:
- name: persistent
  path: /var/log

# Right - use emptyDir for logs
- name: logs
  emptyDir: {}
  path: /var/log
```

## Quick Reference

### Essential Security Context
```yaml
runAsNonRoot: true
runAsGroup: 0
fsGroup: 0
capabilities:
  add: ["SETGID", "SETUID"]
  drop: ["ALL"]
```

### Resource Limits
```yaml
memoryRequest: 512Mi    # Guaranteed
memoryLimit: 4Gi        # Maximum
cpuRequest: 500m        # 0.5 CPU guaranteed
cpuLimit: 2000m         # 2 CPU maximum
```

### Volume Types
- **persistent**: User data, code, configuration
- **emptyDir**: Temporary build artifacts, logs
- **configMap**: Non-sensitive configuration
- **secret**: Sensitive data

### Key Paths
- `/home/user`: User home directory
- `/projects`: Default source code location
- `/tmp`: Temporary files (emptyDir recommended)
- `/.cache`: Package manager caches

## Verification Commands

```bash
# Check current user
id

# Verify security context
kubectl get pod <pod-name> -o yaml | grep -A10 securityContext

# Check volume permissions
ls -la /projects

# Test write permissions
touch /projects/test-file

# View environment variables
env | sort
```

## Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: workspace-policy
spec:
  podSelector:
    matchLabels:
      controller.devfile.io/devworkspace_id: workspace-id
  policyTypes: ["Ingress", "Egress"]
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          app.kubernetes.io/part-of: che
```

## Summary

1. **Always** configure proper security contexts with fsGroup 0
2. **Choose** the right tool: Dockerfile for system setup, devfile for workspace config, runtime for project deps
3. **Separate** persistent data from temporary files using appropriate volume types
4. **Never** hardcode secrets or use sudo in runtime commands
5. **Test** permissions and security contexts early in development
6. **Use `/projects`** for persistent configuration (e.g., `CLAUDE_CONFIG_DIR=/projects/.claude-config`)
7. **Use runtime initialization** with marker files when tools must install to mounted PVCs (see rust-nix-dev pattern)
8. **Set `sandbox = false`** in Nix config for rootless containers
9. **Pre-download installers** to `/tmp` at build time to speed up runtime initialization

## Repository-Specific Notes

For this repository:
- Images built via Jenkins pipelines using shared library pattern
- `udi-plus` triggers cascade builds of derived images
- See `jenkins/JENKINS_SETUP.md` for pipeline setup
- See `containers/rust-dev/Dockerfile` for runtime initialization pattern example
- All devfiles in `devfiles/` directory are production-ready examples

This guide covers Eclipse Che 7.42+ with DevWorkspace Operator v0.19+. For older versions, consult migration documentation.