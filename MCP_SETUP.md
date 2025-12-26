# MCP Server Configuration Guide

This guide covers configuring Model Context Protocol (MCP) servers for Claude Code in Eclipse Che/DevSpaces workspaces.

## Overview

The devfile automatically configures MCP servers on workspace startup via the `setup-claude-mcp` command. Two MCP servers are supported:

| Server | Type | Purpose |
|--------|------|---------|
| **SonarQube** | stdio | Code quality analysis and issue management |
| **Jenkins** | HTTP | CI/CD pipeline management and build monitoring |

## Kubernetes Secrets for Credentials

MCP servers require API tokens that should not be stored in the devfile. Eclipse Che automatically mounts Kubernetes Secrets as environment variables when they have the proper labels.

### Required Labels and Annotations

```yaml
labels:
  controller.devfile.io/mount-to-devworkspace: 'true'
  controller.devfile.io/watch-secret: 'true'
annotations:
  controller.devfile.io/mount-as: 'env'
```

### Combined Secret for All MCP Credentials

You can create a single secret with all MCP credentials:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: mcp-credentials
  labels:
    controller.devfile.io/mount-to-devworkspace: 'true'
    controller.devfile.io/watch-secret: 'true'
  annotations:
    controller.devfile.io/mount-as: 'env'
type: Opaque
stringData:
  # Jenkins MCP
  JENKINS_USERNAME: claude
  JENKINS_TOKEN: <your-jenkins-api-token>
  # SonarQube MCP
  SONARQUBE_TOKEN: <your-sonarqube-token>
```

**Apply to your namespace:**

```bash
kubectl apply -f mcp-credentials.yaml -n <your-che-user-namespace>
```

After creating/updating secrets, restart your workspace for changes to take effect.

---

## SonarQube MCP Server

The [SonarQube MCP Server](https://github.com/SonarSource/sonarqube-mcp-server) enables Claude Code to interact with SonarQube for code quality analysis.

### Environment Variables

| Variable | Source | Description |
|----------|--------|-------------|
| `SONARQUBE_URL` | devfile | SonarQube server URL |
| `SONARQUBE_TOKEN` | K8s secret | SonarQube API token |
| `STORAGE_PATH` | devfile | Local cache directory |

### Getting a SonarQube Token

1. Log into SonarQube
2. Go to **My Account** → **Security**
3. Under "Generate Tokens", enter a name and click **Generate**
4. Copy the token (shown only once)

### Available Tools

Once connected, Claude Code can:
- **get_issues** - List code quality issues
- **get_metrics** - Get project metrics (coverage, bugs, code smells)
- **search_components** - Find projects and components

### Troubleshooting

**Check MCP status:**
```bash
claude mcp list
```

**Connection fails:**
- Verify `SONARQUBE_TOKEN` is set: `echo $SONARQUBE_TOKEN`
- Check SonarQube URL is accessible from workspace
- Ensure token has appropriate permissions

---

## Jenkins MCP Server

The [Jenkins MCP Server Plugin](https://plugins.jenkins.io/mcp-server/) enables Claude Code to interact with Jenkins CI/CD.

### Prerequisites

1. **Jenkins MCP Server Plugin** must be installed on Jenkins
2. **OIDC Configuration** (if using OIDC auth): Enable `allowTokenAccessWithoutOicSession`

See [jenkins/JENKINS_SETUP.md](jenkins/JENKINS_SETUP.md) for detailed Jenkins configuration.

### Environment Variables

| Variable | Source | Description |
|----------|--------|-------------|
| `JENKINS_URL` | devfile | Jenkins server URL |
| `JENKINS_USERNAME` | K8s secret | Jenkins username |
| `JENKINS_TOKEN` | K8s secret | Jenkins API token |

### Getting a Jenkins API Token

1. Log into Jenkins
2. Click your username → **Configure**
3. Under "API Token", click **Add new Token**
4. Copy the generated token (shown only once)

### Available Tools

Once connected, Claude Code can:
- **getJobs** / **getJob** - List and inspect Jenkins jobs
- **triggerBuild** - Start builds with parameters
- **getBuild** / **getBuildLog** - Get build status and logs
- **searchBuildLog** - Search through build logs
- **getJobScm** / **getBuildScm** - Get SCM/git information
- **whoAmI** / **getStatus** - Check authentication and Jenkins health

### Troubleshooting

**Connection fails with 401 Unauthorized:**
- Verify API token is correct and not expired
- Check if `allowTokenAccessWithoutOicSession` is enabled (for OIDC)
- Ensure user has necessary Jenkins permissions

**Connection fails with 404:**
- MCP Server plugin is not installed on Jenkins

---

## Manual MCP Configuration

If you need to manually configure MCP servers (outside of workspace startup):

### SonarQube

```bash
claude mcp add sonarqube \
  --env STORAGE_PATH="/projects/.sonarqube-mcp" \
  --env SONARQUBE_TOKEN="$SONARQUBE_TOKEN" \
  --env SONARQUBE_URL="$SONARQUBE_URL" \
  -- java -jar /opt/mcp/sonarqube-mcp.jar
```

### Jenkins

```bash
JENKINS_AUTH=$(echo -n "$JENKINS_USERNAME:$JENKINS_TOKEN" | base64)
claude mcp add jenkins "$JENKINS_URL/mcp-server/mcp" \
  --transport http \
  --header "Authorization: Basic $JENKINS_AUTH"
```

---

## References

- [Eclipse Che - Mounting Secrets](https://eclipse.dev/che/docs/stable/end-user-guide/mounting-secrets/)
- [SonarQube MCP Server](https://github.com/SonarSource/sonarqube-mcp-server)
- [Jenkins MCP Server Plugin](https://plugins.jenkins.io/mcp-server/)
- [Jenkins OIDC Auth Plugin](https://plugins.jenkins.io/oic-auth/)
