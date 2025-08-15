[![Contribute](https://www.eclipse.org/che/contribute.svg)](https://code.ethosengine.com/#https://github.com/ethosengine/che-devworkspaces) 
# che-devworkspaces

A collection of custom container images and Eclipse Che devfile configurations for enhanced development environments.

## Overview

This repository provides:

- **Custom Container Images**: Enhanced universal developer images with additional tooling
  - `udi-plus`: Universal Developer Image with Claude Code CLI pre-installed
  - `rust-nix-dev`: Rust development environment with Nix package manager and Holochain tooling

  **Custom Builder Images**: Utility images for building and testing
  - `ci-builder`: Multi-tool CI/CD image with Docker, Kubernetes, SonarQube Scanner, and browser testing capabilities

- **Devfile Configurations**: Ready-to-use development workspace definitions
  - Universal polyglot workspace with multiple language support
  - Specialized Rust development environment with cargo tools and persistent caches

- **Eclipse Che Integration**: Seamlessly deployable workspaces for cloud-native development

The images are built and hosted on `harbor.ethosengine.com` and provide instant, reproducible development environments for various programming languages and frameworks.
