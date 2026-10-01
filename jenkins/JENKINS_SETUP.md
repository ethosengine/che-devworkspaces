# Jenkins Pipeline Setup Guide

This guide explains how to set up the individual image build pipelines in Jenkins.

## Overview

The monolithic `Jenkinsfile-devspaces-images` has been split into separate pipelines:

1. **devspaces-ci-builder** - CI builder image (foundational)
2. **devspaces-udi-plus** - Base UDI image (triggers downstream builds)
3. **devspaces-rust-nix-dev** - Rust development image
4. **devspaces-udi-plus-angular** - Angular development image
5. **devspaces-udi-plus-gae** - Google App Engine image
6. **devspaces-udi-plus-mem** - UDI + MemPalace semantic memory (triggers udi-plus-mem-rust-nix)
7. **devspaces-udi-plus-mem-rust-nix** - UDI + MemPalace + Rust/Holochain/Nix
8. **devspaces-base-developer-v2** - x86-64-v2 base image (AlmaLinux 10) for hosts without AVX2 (shem); root of the `VARIANT=v2` chain

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
  - Managed by the Jenkinsfile: `30 2 */3 * *` (02:30 UTC every three days)

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

#### devspaces-base-developer-v2

Create this job by hand (Jenkins does not create it from the repository), the same
way as the others, before the first v2 build.

- **Name**: `devspaces-base-developer-v2`
- **Type**: Pipeline (the cascades call it as `/devspaces-base-developer-v2/main`, so create it in the same multibranch form as its siblings)
- **Pipeline Definition**: Pipeline script from SCM
  - **SCM**: Git
  - **Repository URL**: `https://github.com/ethosengine/che-devworkspaces.git`
  - **Script Path**: `jenkins/Jenkinsfile-base-developer-v2`
  - **Branch**: `*/main`
- **Build Triggers**: none. The v2 chain is built on demand and is never part of the default cascade.

### 2. Configure Credentials

Ensure the following credentials are configured in Jenkins:

- **harbor-robot-registry**: Username/password credential for Harbor registry
  - Username: Harbor robot account username
  - Password: Harbor robot account token

Navigate to **Manage Jenkins** → **Credentials** → **System** → **Global credentials** to add/verify.

## Build Flow

### Automatic Cascade Builds

When `udi-plus` is built successfully:
- It automatically runs these downstream builds in sequence:
  - `devspaces-rust-nix-dev`
  - `devspaces-udi-plus-angular`
  - `devspaces-udi-plus-mem` (which in turn triggers `devspaces-udi-plus-mem-rust-nix`)

Each active image job also uses `disableConcurrentBuilds()`, so a manual,
scheduled, SCM, or upstream invocation that arrives while the same job is
running waits in Jenkins instead of starting a duplicate build.

The `Image` stage owns the Kubernetes agent and cleanup. `Downstream` runs
without an agent after that stage ends, so a parent waiting for its children
does not keep a BuildKit pod alive. Child failures and cancellations propagate.

This is **not yet a global cross-job lock**: separately started chains can
still overlap. The controller currently lacks the Lockable Resources plugin.
After installing it, add `options { lock(resource: 'che-image-build') }` to
each active `Image` stage, before its agent. Never lock the entire pipeline:
a parent holding the lock while waiting for a child would deadlock.

This is configured in the downstream-trigger block of `Jenkinsfile-udi-plus`:

```groovy
if (env.BUILD_RESULT == 'SUCCESS' && !params.SKIP_PUSH) {
    echo 'udi-plus updated - building downstream images sequentially'
    build job: '/devspaces-rust-nix-dev/main', wait: true
    build job: '/devspaces-udi-plus-angular/main', wait: true
    build job: '/devspaces-udi-plus-mem/main', wait: true
}
```

The absolute paths name concrete multibranch `main` jobs. With `wait: true`,
targeting only the parent folder fails with `Waiting for non-job items is not
supported` instead of scheduling a build.

### The x86-64-v2 chain (`VARIANT=v2`)

shem's CPUs have no AVX2, and the UBI 10 userland requires x86-64-v3, so the default
images cannot start there. The v2 chain is the same three Dockerfiles on an
AlmaLinux 10 x86-64-v2 base. See `docs/2026-10-01-x86-64-v2-workspace-image-plan.md`.

```text
devspaces-base-developer-v2                  (containers/base-developer-v2)
  -> devspaces-udi-plus          VARIANT=v2  (BASE_IMAGE=devspaces/base-developer-v2:latest)
    -> devspaces-udi-plus-mem          VARIANT=v2 BASE_TAG=v2-latest
      -> devspaces-udi-plus-mem-rust-nix   VARIANT=v2 BASE_TAG=v2-latest
```

Run `devspaces-base-developer-v2`; it cascades through the rest with the right
parameters. To build one layer alone, set `VARIANT=v2` (and, below `udi-plus`,
`BASE_TAG=v2-latest` or a `v2-` dated tag; a mismatched `VARIANT`/`BASE_TAG` pair
fails the build).

With `VARIANT=v2`:

- Tags are `v2-latest`, `v2-<date>`, `v2-<git>`. The default `latest`, `<date>` and
  `<git>` tags are never pushed, moved or pruned (Harbor cleanup matches each
  chain's dated tags separately).
- The build pushes only the `v2-<date>` and `v2-<git>` tags (a candidate).
  `Smoke on shem` then runs `containers/smoke/v2-smoke.sh` in a pod pinned to shem
  (`kubernetes.io/hostname: shem`, toleration `remote-wan=true:NoSchedule`) inside
  the candidate. Only if it passes does `smokeAndPromoteDevspaceImage` add the
  `v2-latest` tag through the Harbor API (a retag, no rebuild). A failed smoke fails
  the build, leaves `v2-latest` where it was and stops the cascade.
- The `udi-plus` cascade runs only `udi-plus-mem` (and from it `udi-plus-mem-rust-nix`):
  `rust-nix-dev` and `udi-plus-angular` build on the default chain and are not part of v2.
- Requirements on the cluster: the Jenkins agent image must itself run on x86-64-v2
  (the pod's agent container lands on shem too), and the `harbor-robot-registry`
  credential must be allowed to create tags.

### Manual Builds

To build an individual image:

1. Navigate to the pipeline job
2. Click **Build with Parameters**
3. Configure options:
   - **FORCE_BUILD**: Force rebuild even if base image hasn't changed
   - **BASE_TAG** (for derived images): Specify which udi-plus tag to build from
   - **VARIANT** (udi-plus and its derived images): `default` (UBI10, `latest`) or `v2` (x86-64-v2, `v2-latest`, smoke on shem); see above
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

The two scheduled roots run in UTC; descendants are cascade-driven and do not
have independent timers:

- `Jenkinsfile-udi-plus`: `30 2 */3 * *` (02:30 UTC)
- `Jenkinsfile-ci-builder`: `0 3 1,15 * *` (03:00 UTC on the 1st and 15th)

Modify the relevant root's `triggers.cron` to change the cadence. Jenkins cron
interprets `*/3` in the day-of-month field as every third calendar day within
each month.

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
