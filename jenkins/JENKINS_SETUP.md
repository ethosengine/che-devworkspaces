# Jenkins Pipeline Setup Guide

This guide explains how to set up the individual image build pipelines in Jenkins.

## Overview

The monolithic `Jenkinsfile-devspaces-images` has been split into 7 separate pipelines:

1. **devspaces-ci-builder** - CI builder image (foundational)
2. **devspaces-udi-plus** - Base UDI image (triggers downstream builds)
3. **devspaces-rust-nix-dev** - Rust development image
4. **devspaces-udi-plus-angular** - Angular development image
5. **devspaces-udi-plus-gae** - Google App Engine image
6. **devspaces-udi-plus-mem** - UDI + MemPalace semantic memory (triggers udi-plus-mem-rust-nix)
7. **devspaces-udi-plus-mem-rust-nix** - UDI + MemPalace + Rust/Holochain/Nix

## Shared Library Setup

First, configure the Jenkins shared library that contains common build logic:

### Option 1: Configure as Global Shared Library (Recommended)

1. Go to **Manage Jenkins** → **Configure System**
2. Scroll to **Global Pipeline Libraries**
3. Click **Add** and configure:
   - **Name**: `imagebuilder-shared`
   - **Default version**: `main` (or your default branch)
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: `https://github.com/ethosengine/che-devworkspaces.git`
   - **Library Path**: `jenkins/shared-library`
4. Click **Save**

### Option 2: Configure Per-Pipeline

If you prefer to not use global shared libraries, each Jenkinsfile can load the library explicitly:

```groovy
@Library('imagebuilder-shared@main') _
// or
library identifier: 'imagebuilder-shared@main', retriever: modernSCM([
  $class: 'GitSCMSource',
  remote: 'https://github.com/ethosengine/che-devworkspaces.git',
  credentialsId: 'github-credentials'
])
```

## Pipeline Job Setup

For each image, create a Pipeline job in Jenkins:

### 1. Create Pipeline Jobs

Navigate to Jenkins → **New Item** for each of the following:

#### devspaces-ci-builder

- **Name**: `devspaces-ci-builder`
- **Type**: Pipeline
- **Pipeline Definition**: Pipeline script from SCM
  - **SCM**: Git
  - **Repository URL**: `https://github.com/ethosengine/che-devworkspaces.git`
  - **Script Path**: `jenkins/Jenkinsfile-ci-builder`
  - **Branch**: `*/main`

#### devspaces-udi-plus

- **Name**: `devspaces-udi-plus`
- **Type**: Pipeline
- **Pipeline Definition**: Pipeline script from SCM
  - **SCM**: Git
  - **Repository URL**: `https://github.com/ethosengine/che-devworkspaces.git`
  - **Script Path**: `jenkins/Jenkinsfile-udi-plus`
  - **Branch**: `*/main`
- **Build Triggers**:
  - ✅ Poll SCM: `H 2 * * *` (daily at 2 AM to check for base image updates)

#### devspaces-rust-nix-dev

- **Name**: `devspaces-rust-nix-dev`
- **Type**: Pipeline
- **Pipeline Definition**: Pipeline script from SCM
  - **SCM**: Git
  - **Repository URL**: `https://github.com/ethosengine/che-devworkspaces.git`
  - **Script Path**: `jenkins/Jenkinsfile-rust-nix-dev`
  - **Branch**: `*/main`

#### devspaces-udi-plus-angular

- **Name**: `devspaces-udi-plus-angular`
- **Type**: Pipeline
- **Pipeline Definition**: Pipeline script from SCM
  - **SCM**: Git
  - **Repository URL**: `https://github.com/ethosengine/che-devworkspaces.git`
  - **Script Path**: `jenkins/Jenkinsfile-udi-plus-angular`
  - **Branch**: `*/main`

#### devspaces-udi-plus-gae

- **Name**: `devspaces-udi-plus-gae`
- **Type**: Pipeline
- **Pipeline Definition**: Pipeline script from SCM
  - **SCM**: Git
  - **Repository URL**: `https://github.com/ethosengine/che-devworkspaces.git`
  - **Script Path**: `jenkins/Jenkinsfile-udi-plus-gae`
  - **Branch**: `*/main`

#### devspaces-udi-plus-mem

- **Name**: `devspaces-udi-plus-mem`
- **Type**: Pipeline
- **Pipeline Definition**: Pipeline script from SCM
  - **SCM**: Git
  - **Repository URL**: `https://github.com/ethosengine/che-devworkspaces.git`
  - **Script Path**: `jenkins/Jenkinsfile-udi-plus-mem`
  - **Branch**: `*/main`

#### devspaces-udi-plus-mem-rust-nix

- **Name**: `devspaces-udi-plus-mem-rust-nix`
- **Type**: Pipeline
- **Pipeline Definition**: Pipeline script from SCM
  - **SCM**: Git
  - **Repository URL**: `https://github.com/ethosengine/che-devworkspaces.git`
  - **Script Path**: `jenkins/Jenkinsfile-udi-plus-mem-rust-nix`
  - **Branch**: `*/main`

### 2. Configure Credentials

Ensure the following credentials are configured in Jenkins:

- **harbor-robot-registry**: Username/password credential for Harbor registry
  - Username: Harbor robot account username
  - Password: Harbor robot account token

Navigate to **Manage Jenkins** → **Credentials** → **System** → **Global credentials** to add/verify.

## Build Flow

### Automatic Cascade Builds

When `udi-plus` is built successfully:
- It automatically triggers builds of:
  - `devspaces-rust-nix-dev`
  - `devspaces-udi-plus-angular`
  - `devspaces-udi-plus-gae`
  - `devspaces-udi-plus-mem` (which in turn triggers `devspaces-udi-plus-mem-rust-nix`)

This is configured in the downstream-trigger block of `Jenkinsfile-udi-plus`:

```groovy
if (!result.skipped && !params.SKIP_PUSH) {
    echo 'udi-plus updated - triggering downstream builds'
    build job: 'devspaces-rust-nix-dev', wait: false
    build job: 'devspaces-udi-plus-angular', wait: false
    build job: 'devspaces-udi-plus-gae', wait: false
    build job: 'devspaces-udi-plus-mem', wait: false
}
```

### Manual Builds

To build an individual image:

1. Navigate to the pipeline job
2. Click **Build with Parameters**
3. Configure options:
   - **FORCE_BUILD**: Force rebuild even if base image hasn't changed
   - **BASE_TAG** (for derived images): Specify which udi-plus tag to build from
   - **SKIP_PUSH**: Test builds without pushing to registry
   - **SKIP_SECURITY_SCAN**: Skip Harbor security scanning
   - **SKIP_SMOKE_TESTS**: Skip smoke tests
4. Click **Build**

## Build Status Badges

Jenkins badges are embedded in the README.md using the Embeddable Build Status plugin.

Badge URL format:
```
https://jenkins.ethosengine.com/buildStatus/icon?job=<job-name>
```

If badges don't appear:
1. Install the **Embeddable Build Status** plugin
2. Go to **Manage Jenkins** → **Configure Global Security**
3. Ensure anonymous users have **Read** access to jobs

## Troubleshooting

### Shared library not found

**Error**: `Library imagebuilder-shared not found`

**Solution**: Verify the shared library is configured in Jenkins (see Shared Library Setup above)

### Base image check fails

**Error**: Image update check fails on udi-plus build

**Solution**: Use `FORCE_BUILD=true` parameter to skip base image checking

### Downstream builds not triggered

**Error**: udi-plus builds successfully but doesn't trigger child images

**Solution**: Ensure all downstream job names match exactly:
- `devspaces-rust-nix-dev`
- `devspaces-udi-plus-angular`
- `devspaces-udi-plus-gae`
- `devspaces-udi-plus-mem` (and its own downstream: `devspaces-udi-plus-mem-rust-nix`)

### Permission denied on Harbor

**Error**: Push fails with 401/403

**Solution**: Verify `harbor-robot-registry` credentials are configured correctly

## Migration from Monolithic Pipeline

If you're migrating from the old `Jenkinsfile-devspaces-images`:

1. Keep the old pipeline job for now (rename it to `devspaces-images-legacy`)
2. Set up all new individual pipelines
3. Run a test build of each new pipeline with `SKIP_PUSH=true`
4. Once verified, disable the old pipeline
5. Update any external references to point to new job names

## Advanced Configuration

### Custom Build Agent

To use a different Kubernetes pod template, modify the `agent.kubernetes.yaml` section in each Jenkinsfile.

### Custom Registry

To push to a different registry, update the `registry` parameter in each pipeline's `buildDevspaceImage()` call.

### Adjust Build Schedule

Modify the `triggers.cron` in `Jenkinsfile-udi-plus` to change when automatic base image checks occur.

Default: `0 2 * * *` (2 AM daily)

## MCP Server Plugin Configuration

The [Jenkins MCP Server plugin](https://plugins.jenkins.io/mcp-server/) enables Claude Code to interact with Jenkins directly through the Model Context Protocol.

### Plugin Installation

1. Go to **Manage Jenkins** → **Plugins** → **Available plugins**
2. Search for "MCP Server"
3. Install and restart Jenkins

The plugin automatically exposes endpoints at `/mcp-server/mcp` (HTTP), `/mcp-server/sse` (SSE), and `/mcp-server/message`.

### OIDC Authentication Requirement

**Important:** If Jenkins uses OIDC authentication (OpenID Connect), API tokens will not work by default. The OIDC plugin requires an active browser session for token authentication.

To enable API token access for MCP and other scripted clients:

**Via Jenkins Configuration as Code (JCasC):**
```yaml
jenkins:
  securityRealm:
    oic:
      allowTokenAccessWithoutOicSession: true
```

**Via UI:**
1. Go to **Manage Jenkins** → **Security**
2. Under Security Realm (OpenID Connect), enable **"Allow API token access without OIC session"**

This allows service accounts like `claude` to authenticate via API token without maintaining an active browser login.

### MCP Client Configuration

The devfile configures Claude Code MCP automatically on workspace start. Manual configuration:

```bash
# Generate base64 credentials
JENKINS_AUTH=$(echo -n "username:api-token" | base64)

# Add Jenkins MCP server
claude mcp add jenkins "https://jenkins.example.com/mcp-server/mcp" \
  --transport http \
  --header "Authorization: Basic $JENKINS_AUTH"
```

### Kubernetes Secret for Credentials

Jenkins credentials are injected into workspaces via Kubernetes Secrets with Eclipse Che automount labels. The devfile references `$JENKINS_USERNAME` and `$JENKINS_TOKEN` environment variables that must be provided by a secret.

> **Tip:** You can combine Jenkins and SonarQube credentials in a single secret. See [MCP_SETUP.md](../MCP_SETUP.md) for the combined approach.

**Create the secret in your user namespace:**

```bash
# Create secret with Jenkins credentials
kubectl create secret generic jenkins-mcp-credentials \
  --from-literal=JENKINS_USERNAME=claude \
  --from-literal=JENKINS_TOKEN=<your-jenkins-api-token> \
  -n <your-che-user-namespace> \
  --dry-run=client -o yaml | kubectl apply -f -

# Add automount labels and annotation
kubectl label secret jenkins-mcp-credentials \
  controller.devfile.io/mount-to-devworkspace=true \
  controller.devfile.io/watch-secret=true \
  -n <your-che-user-namespace>

kubectl annotate secret jenkins-mcp-credentials \
  controller.devfile.io/mount-as=env \
  -n <your-che-user-namespace>
```

**Or apply this YAML directly:**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: jenkins-mcp-credentials
  labels:
    controller.devfile.io/mount-to-devworkspace: 'true'
    controller.devfile.io/watch-secret: 'true'
  annotations:
    controller.devfile.io/mount-as: 'env'
type: Opaque
stringData:
  JENKINS_USERNAME: claude
  JENKINS_TOKEN: <your-jenkins-api-token>
```

The secret keys become environment variables in all workspace containers. After creating/updating the secret, restart your workspace for changes to take effect.

**To get your Jenkins API token:**
1. Log into Jenkins
2. Click your username → Configure
3. Under "API Token", click "Add new Token"
4. Copy the generated token (it won't be shown again)

### Available MCP Tools

Once connected, Claude Code can:
- **getJobs** / **getJob** - List and inspect Jenkins jobs
- **triggerBuild** - Start builds with parameters
- **getBuild** / **getBuildLog** - Get build status and logs
- **searchBuildLog** - Search through build logs
- **getJobScm** / **getBuildScm** - Get SCM/git information
- **whoAmI** / **getStatus** - Check authentication and Jenkins health

### Troubleshooting MCP Connection

**Connection fails with 401 Unauthorized:**
- Verify API token is correct and not expired
- Check if `allowTokenAccessWithoutOicSession` is enabled (for OIDC)
- Ensure user has necessary Jenkins permissions

**Connection fails with 404:**
- MCP Server plugin is not installed

**Check MCP status:**
```bash
claude mcp list
```
