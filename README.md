[![Contribute](https://www.eclipse.org/che/contribute.svg)](https://code.ethosengine.com/#https://github.com/ethosengine/che-devworkspaces) 
# che-devworkspaces

A collection of custom container images and Eclipse Che devfile configurations for enhanced development environments.

## Container Images

All images are built via Jenkins pipelines and hosted on Harbor registry.

| Image | Build Status | Registry | Description |
|-------|-------------|----------|-------------|
| **ci-builder** | [![Build Status](https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-ci-builder)](https://jenkins.ethosengine.com/job/devspaces-ci-builder/) | [harbor.ethosengine.com/ethosengine/ci-builder](https://harbor.ethosengine.com/harbor/projects/2/repositories/ci-builder) | Multi-tool CI/CD image with nerdctl, buildctl, kubectl, SonarQube scanner |
| **udi-plus** | [![Build Status](https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus)](https://jenkins.ethosengine.com/job/devspaces-udi-plus/) | [harbor.ethosengine.com/devspaces/udi-plus](https://harbor.ethosengine.com/harbor/projects/3/repositories/udi-plus) | Base universal developer image with Claude Code CLI pre-installed |
| **rust-nix-dev** | [![Build Status](https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-rust-nix-dev)](https://jenkins.ethosengine.com/job/devspaces-rust-nix-dev/) | [harbor.ethosengine.com/devspaces/rust-nix-dev](https://harbor.ethosengine.com/harbor/projects/3/repositories/rust-nix-dev) | Rust development environment with Nix package manager and Holochain tooling |
| **udi-plus-angular** | [![Build Status](https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus-angular)](https://jenkins.ethosengine.com/job/devspaces-udi-plus-angular/) | [harbor.ethosengine.com/devspaces/udi-plus-angular](https://harbor.ethosengine.com/harbor/projects/3/repositories/udi-plus-angular) | Angular development environment based on udi-plus |
| **udi-plus-gae** | [![Build Status](https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus-gae)](https://jenkins.ethosengine.com/job/devspaces-udi-plus-gae/) | [harbor.ethosengine.com/devspaces/udi-plus-gae](https://harbor.ethosengine.com/harbor/projects/3/repositories/udi-plus-gae) | Google App Engine environment with Python 2.7 support |

### Image Hierarchy

```
quay.io/devfile/universal-developer-image:ubi9-latest
  └─> udi-plus (base image with Claude Code)
       ├─> rust-nix-dev (Rust + Nix + Holochain)
       ├─> udi-plus-angular (Angular + Node.js)
       └─> udi-plus-gae (GAE + Python 2.7)
```

## Overview

This repository provides:

- **Custom Container Images**: Enhanced universal developer images with additional tooling (see table above)
- **Devfile Configurations**: Ready-to-use development workspace definitions
  - Universal polyglot workspace with multiple language support
  - Specialized Rust development environment with cargo tools and persistent caches
- **Eclipse Che Integration**: Seamlessly deployable workspaces for cloud-native development

All images provide instant, reproducible development environments for various programming languages and frameworks.
