# Eclipse Che DevWorkspaces Guide

A practical reference for building secure, efficient DevWorkspaces in Eclipse Che and OpenShift Dev Spaces.

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

This guide covers Eclipse Che 7.42+ with DevWorkspace Operator v0.19+. For older versions, consult migration documentation.