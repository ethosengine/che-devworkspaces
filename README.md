[![Contribute](https://www.eclipse.org/che/contribute.svg)](https://code.ethosengine.com/#https://github.com/ethosengine/che-devworkspaces) 
# che-devworkspaces

A collection of custom container images and Eclipse Che devfile configurations for enhanced development environments.

## Container Images

All images are built via Jenkins pipelines and hosted on Harbor registry.

<table>
<thead>
<tr>
<th width="15%">Image</th>
<th width="15%">Build Status</th>
<th width="70%">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/3/repositories/ci-builder"><strong>ci-builder</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/ethosengine-ci-builder/job/main/"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=ethosengine-ci-builder%2Fmain" alt="Build Status"></a></td>
<td>Multi-tool CI/CD image with nerdctl, buildctl, kubectl, SonarQube scanner</td>
</tr>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/3/repositories/udi-plus"><strong>udi-plus</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/devspaces-udi-plus/job/main/"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus%2Fmain" alt="Build Status"></a></td>
<td>Base universal developer image with Claude Code CLI pre-installed</td>
</tr>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/3/repositories/rust-nix-dev"><strong>rust-nix-dev</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/devspaces-rust-nix-dev/job/main/"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-rust-nix-dev%2Fmain" alt="Build Status"></a></td>
<td>Rust development environment with Nix package manager and Holochain tooling</td>
</tr>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/3/repositories/udi-plus-angular"><strong>udi-plus-angular</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/devspaces-udi-plus-angular/job/main/"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus-angular%2Fmain" alt="Build Status"></a></td>
<td>Angular development based on udi-plus</td>
</tr>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/3/repositories/udi-plus-gae"><strong>udi-plus-gae</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/devspaces-udi-plus-gae/job/main/"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus-gae%2Fmain" alt="Build Status"></a></td>
<td>Google App Engine with Python 2.7 support</td>
</tr>
</tbody>
</table>

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
