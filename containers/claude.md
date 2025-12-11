# Container Development Guidelines for Eclipse Che

## Critical: Environment Variables and PATH in Containers

### The Problem

Modifications to `/home/user` in Dockerfiles (including `.bashrc`, `.profile`, and custom scripts) **do work** and survive into the running container. However, they may not take effect in all execution contexts.

### Why `.bashrc` Modifications Don't Work Everywhere

`.bashrc` is only sourced for **interactive bash shells**. Many tools and contexts in Eclipse Che do NOT source `.bashrc`:

- Claude Code sessions (runs in non-interactive shell)
- VS Code background tasks and extensions
- DevWorkspace container init processes
- Cron jobs and systemd services
- Scripts invoked via `sh` instead of `bash`
- Non-login shell sessions

### What Actually Works

1. **Files in `/home/user/` DO survive** (unless explicitly overwritten by volume mounts)
2. **Scripts in `/home/user/bin/` ARE preserved** and executable
3. **`.bashrc` modifications ARE present** but only apply to interactive shells

### The Solution: Use DevWorkspace `env` Section

The **only reliable way** to set environment variables (including PATH) for all contexts is through the devfile's `env` section:

```yaml
components:
  - name: dev
    container:
      image: your-image
      env:
        - name: PATH
          value: /home/user/bin:/opt/custom/bin:${PATH}
        - name: CUSTOM_VAR
          value: some-value
```

### Guidelines for Container Authors

#### DO:
- Install binaries to well-known locations (`/usr/local/bin`, `/opt/`)
- Create scripts in `/home/user/bin/` for user convenience
- Document required `env` entries that devfile authors must add
- Use `ENV` in Dockerfile for build-time and default values

#### DON'T:
- Rely solely on `.bashrc` or `.profile` for PATH modifications
- Assume interactive shell behavior in all contexts
- Expect `/home/user/.bashrc` changes to affect all tools

### Example: Adding Custom Scripts

**In Dockerfile:**
```dockerfile
# Install scripts
COPY scripts/ /home/user/bin/
RUN chmod +x /home/user/bin/*

# Set default PATH (works for interactive shells and as documentation)
RUN echo 'export PATH=/home/user/bin:$PATH' >> /home/user/.bashrc

# Also set via ENV for processes that read it
ENV PATH=/home/user/bin:$PATH
```

**In devfile.yaml (required for full coverage):**
```yaml
env:
  - name: PATH
    value: /home/user/bin:${PATH}
```

### Volume Mount Considerations

DevWorkspaces may mount volumes to subdirectories of `/home/user/`:
- `.cargo/` for Rust cargo cache
- `.rustup/` for Rust toolchains
- `.npm/` for npm cache

These mounts **overlay** Dockerfile contents at those paths. Files outside mounted directories survive intact.

### Debugging Tips

```bash
# Check if script exists and is executable
ls -la /home/user/bin/

# Check current PATH
echo $PATH | tr ':' '\n'

# Check what .bashrc contains
grep PATH /home/user/.bashrc

# Test script directly
/home/user/bin/your-script

# Source bashrc manually to test
source /home/user/.bashrc && echo $PATH
```

### Summary

| Method | Interactive Shell | VS Code Tasks | Claude Code | Background Jobs |
|--------|------------------|---------------|-------------|-----------------|
| `.bashrc` | Yes | No | No | No |
| Dockerfile `ENV` | Maybe | Maybe | Maybe | Maybe |
| devfile `env` | Yes | Yes | Yes | Yes |

**Always use devfile `env` section for critical PATH and environment variables.**

## Dockerfile Syntax Guidelines

### DO NOT Use Heredocs in Dockerfiles

Heredoc syntax (`<< 'EOF'` or `<< 'SCRIPT'`) does NOT work in Dockerfiles. The Docker parser will interpret lines after the heredoc delimiter as Dockerfile instructions, causing errors like:

```
error: unknown instruction: echo
```

**BAD - Will fail:**
```dockerfile
RUN cat > /home/user/bin/script << 'EOF'
#!/bin/bash
echo "Hello"
EOF
```

**GOOD - Use echo chains:**
```dockerfile
RUN echo '#!/bin/bash' > /home/user/bin/script && \
    echo 'echo "Hello"' >> /home/user/bin/script && \
    chmod +x /home/user/bin/script
```

**GOOD - Use COPY for complex scripts:**
```dockerfile
COPY scripts/my-script.sh /home/user/bin/my-script
RUN chmod +x /home/user/bin/my-script
```

### Script Generation Pattern

When generating scripts in Dockerfiles, use the echo-append pattern:

```dockerfile
RUN echo '#!/bin/bash' > /path/to/script && \
    echo 'line 1' >> /path/to/script && \
    echo 'line 2' >> /path/to/script && \
    chmod +x /path/to/script
```

For escaping single quotes within the script, use `'\''`:
```dockerfile
RUN echo 'echo "It'\''s working"' >> /path/to/script
```
