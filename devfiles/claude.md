# Devfile Development Guidelines for Eclipse Che

## Critical: Environment Variables Must Be Set in Devfile

### The Problem

Container images may include PATH modifications in `.bashrc` or set `ENV` in Dockerfile, but these **do not reliably propagate** to all execution contexts in Eclipse Che DevWorkspaces.

### Why This Happens

Eclipse Che runs containers in various contexts that don't source shell configuration files:

| Context | Sources `.bashrc`? | Reads Dockerfile `ENV`? |
|---------|-------------------|------------------------|
| Interactive terminal | Yes | Yes |
| VS Code extensions | No | Sometimes |
| Claude Code | No | Sometimes |
| IDE background tasks | No | Sometimes |
| Container init | No | Yes |

### The Solution

**Always explicitly set environment variables in the devfile's `env` section:**

```yaml
components:
  - name: dev
    container:
      image: quay.io/yourorg/your-image:tag
      env:
        # PATH must include custom binary locations
        - name: PATH
          value: /home/user/bin:/opt/custom/bin:${PATH}

        # Any other required environment variables
        - name: SOME_SDK_HOME
          value: /opt/sdk
```

### Common Patterns

#### Adding Custom Scripts to PATH

If your container has scripts in `/home/user/bin/`:

```yaml
env:
  - name: PATH
    value: /home/user/bin:${PATH}
```

#### Rust/Cargo Development

```yaml
env:
  - name: PATH
    value: /home/user/.cargo/bin:${PATH}
  - name: CARGO_HOME
    value: /home/user/.cargo
  - name: RUSTUP_HOME
    value: /home/user/.rustup
```

#### Nix Package Manager

```yaml
env:
  - name: PATH
    value: /home/user/bin:/nix/var/nix/profiles/default/bin:${PATH}
  - name: NIX_PROFILES
    value: /nix/var/nix/profiles/default
```

#### Node.js Global Packages

```yaml
env:
  - name: PATH
    value: /home/user/.npm-global/bin:${PATH}
  - name: NPM_CONFIG_PREFIX
    value: /home/user/.npm-global
```

### Volume Mounts and Persistence

When using `persistentVolumeClaims` or `volumes`, be aware:

1. Mounted paths **overlay** container image contents at that location
2. First-run initialization may be needed to populate mounted directories
3. Files outside mounted paths come from the container image

```yaml
components:
  - name: dev
    container:
      volumeMounts:
        - name: cargo-cache
          path: /home/user/.cargo    # Overlays image contents
        - name: rustup
          path: /home/user/.rustup   # Overlays image contents
      # /home/user/bin is NOT mounted, so image contents survive
```

### Debugging Environment Issues

If tools or commands aren't found:

1. **Check the devfile `env` section** - is PATH set correctly?
2. **Verify the container image** - do the binaries exist?
3. **Test in terminal** - `echo $PATH | tr ':' '\n'`
4. **Check what the image provides** - `ls -la /home/user/bin/`

### Checklist for Devfile Authors

- [ ] Set `PATH` in `env` section including all custom binary locations
- [ ] Include language-specific environment variables (CARGO_HOME, GOPATH, etc.)
- [ ] Document any required environment variables from the container image
- [ ] Test tools work in both terminal AND IDE extensions/background tasks
- [ ] Verify Claude Code can run expected commands

### Anti-Patterns to Avoid

**Don't rely on container `.bashrc`:**
```yaml
# BAD: Assumes .bashrc PATH will work everywhere
components:
  - name: dev
    container:
      image: my-image  # Has PATH in .bashrc but not in env
```

**Don't assume Dockerfile ENV is enough:**
```yaml
# RISKY: Dockerfile ENV may not propagate to all contexts
components:
  - name: dev
    container:
      image: my-image  # Has ENV PATH=... in Dockerfile
      # Should still explicitly set env here for reliability
```

**Do be explicit:**
```yaml
# GOOD: Explicitly set all required environment variables
components:
  - name: dev
    container:
      image: my-image
      env:
        - name: PATH
          value: /home/user/bin:/custom/path:${PATH}
```

### Summary

The devfile `env` section is the **single source of truth** for environment variables in Eclipse Che. Container images should document what environment variables they need, and devfile authors must explicitly set them. Never assume shell configuration files or Dockerfile ENV will be sufficient.
