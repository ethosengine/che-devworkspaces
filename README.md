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
<td><a href="https://harbor.ethosengine.com/harbor/projects/3/repositories/ci-builder" target="_blank"><strong>harbor.ethosengine.com/ethosengine/ci-builder</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/ethosengine-ci-builder/job/main/" target="_blank"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=ethosengine-ci-builder%2Fmain" alt="Build Status"></a></td>
<td>Multi-tool CI/CD image with nerdctl, buildctl, kubectl, SonarQube scanner</td>
</tr>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/4/repositories/udi-plus" target="_blank"><strong>harbor.ethosengine.com/devspaces/udi-plus</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/devspaces-udi-plus/job/main/" target="_blank"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus%2Fmain" alt="Build Status"></a></td>
<td>Base universal developer image with Claude Code CLI pre-installed</td>
</tr>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/4/repositories/rust-nix-dev" target="_blank"><strong>harbor.ethosengine.com/devspaces/rust-nix-dev</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/devspaces-rust-nix-dev/job/main/" target="_blank"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-rust-nix-dev%2Fmain" alt="Build Status"></a></td>
<td>Rust development environment with Nix package manager and Holochain tooling</td>
</tr>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/4/repositories/udi-plus-angular" target="_blank"><strong>harbor.ethosengine.com/devspaces/udi-plus-angular</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/devspaces-udi-plus-angular/job/main/" target="_blank"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus-angular%2Fmain" alt="Build Status"></a></td>
<td>Angular development based on udi-plus</td>
</tr>
<tr>
<td><a href="https://harbor.ethosengine.com/harbor/projects/4/repositories/udi-plus-gae" target="_blank"><strong>harbor.ethosengine.com/devspaces/udi-plus-gae</strong></a></td>
<td><a href="https://jenkins.ethosengine.com/view/ethosimages/job/devspaces-udi-plus-gae/job/main/" target="_blank"><img src="https://jenkins.ethosengine.com/buildStatus/icon?job=devspaces-udi-plus-gae%2Fmain" alt="Build Status"></a></td>
<td>Google App Engine with Python 2.7 support — <strong>ARCHIVED</strong>, manual builds only (not in the udi-plus cascade)</td>
</tr>
</tbody>
</table>

### Image Hierarchy

```
# CI/CD Builder Image (independent)
ci-builder (standalone multi-tool CI/CD image)

# Development Environment Images
quay.io/devfile/universal-developer-image:ubi9-latest
  └─> udi-plus (base image with Claude Code)
       ├─> rust-nix-dev (Rust + Nix + Holochain)
       ├─> udi-plus-angular (Angular + Node.js)
       └─> udi-plus-gae (GAE + Python 2.7) — ARCHIVED, manual builds only
```

## Overview

This repository provides:

- **Custom Container Images**: Enhanced universal developer images with additional tooling (see table above)
- **Devfile Configurations**: Ready-to-use development workspace definitions
  - Universal polyglot workspace with multiple language support
  - Specialized Rust development environment with cargo tools and persistent caches
- **Eclipse Che Integration**: Seamlessly deployable workspaces for cloud-native development
- **MCP Server Integration**: Pre-configured [SonarQube and Jenkins MCP servers](MCP_SETUP.md) for Claude Code

All images provide instant, reproducible development environments for various programming languages and frameworks.

## Choosing the host for a new Elohim workspace

As of 2026-09-30, ordinary Che workspaces use a separate `openebs-hostpath`
PVC per workspace on the LAN. To establish another large Elohim workspace on
**shem**, select both the node and `shem-zfs` storage before its first start.
A node selector alone does not change Che's storage class.

The Elohim monorepo carries a shem variant of its `devfile.yaml`,
`devfile-shem.yaml`, with the same images, commands and volumes. It differs in
its name, its memory and CPU settings (see below), and these entries in the
top-level `attributes` map, which keep the root devfile's
`fsGroupChangePolicy: OnRootMismatch` setting:

```yaml
attributes:
  controller.devfile.io/storage-type: per-workspace
  controller.devfile.io/devworkspace-config:
    name: devworkspace-config-shem
    namespace: eclipse-che
  pod-overrides:
    spec:
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
              - matchExpressions:
                  - key: kubernetes.io/hostname
                    operator: In
                    values:
                      - shem
      tolerations:
        - key: remote-wan
          operator: Equal
          value: 'true'
          effect: NoSchedule
      securityContext:
        fsGroupChangePolicy: OnRootMismatch
```

Placement is node affinity, not `nodeSelector`: the CheCluster's
`devEnvironments.nodeSelector` (`node-type=performance` as of 2026-10-01)
replaces any `nodeSelector` a devfile sets. A test workspace with a
`hostname=shem` selector came out with `node-type=performance` only and
scheduled on ethosengine. Affinity survives but is ANDed with that selector, so
**the shem variant stays Pending until the CheCluster selector admits shem**,
for example a `che-workspaces=true` label on both nodes with the CheCluster
pointed at it. Changing that selector alters every workspace's pod template,
so running workspaces restart at the operator's next reconcile; schedule it.
Do not relabel shem as `performance`: the zfs controller's anti-affinity keys
on `node-type=remote`.

The affinity requires shem; the toleration permits scheduling through its WAN
taint, and ordinary workspaces lack it, so they stay on the LAN node. If shem is unavailable or lacks resources, this workspace waits rather
than falling back to a LAN node. With a new `shem-zfs` claim, first-consumer
binding establishes the volume on shem and its PV affinity keeps it there.
For an ethosengine variant, use the value `ethosengine`, omit the
remote toleration, and retain the normal Che storage configuration.

### Storage configuration prerequisite (operator-owned)

Before launching the shem variant, ops must provision
`devworkspace-config-shem` in the Che installation namespace, `eclipse-che`. Base it on
Che's current `devworkspace-config` so routing, security and other workspace
settings are preserved, and set this field:

```yaml
# Inside the alternate DevWorkspaceOperatorConfig:
config:
  workspace:
    storageClassName: shem-zfs
```

The namespace in the devfile must match that configuration. This is an
alternate configuration for selected workspaces, not a change to every Che
workspace's default. Che Dashboard adds its own configuration reference only
when the devfile carries none (checked against dashboard 7.122.0 on
2026-10-01), so the shem reference survives creation. Still confirm the created
PVC's class: a claim's storage class cannot be changed afterwards, and the node
selector is not sufficient by itself.

`shem-zfs` (verified 2026-10-01) binds on first consumer, allows expansion and
has reclaim policy `Retain`. Deleting the workspace therefore leaves its PV,
zfsvolume and dataset behind for manual removal.

This repository does not include the live CheCluster or that alternate
configuration. The snippets above describe the setup to add; whether it is
provisioned is a cluster fact to check before launching.

### Memory, CPU and snapshots

Memory is not shem's constraint: about 110Gi is allocatable. CPU is. shem has
24 threads of 2013-era Ivy Bridge and hosts fleet peers that were already using
about 9.4 of them (30-minute average, 2026-10-01). Under contention the kernel
shares CPU in proportion to requests, and each fleet conductor requests 125m,
so a workspace with a large CPU request starves the peers a build runs beside.
`devfile-shem.yaml` therefore sets a 40Gi memory limit, a CPU limit of 8 and a
CPU request of 2.

Every `tank/k8s` dataset is snapshotted hourly, and a volume's quota counts its
snapshots. Build output churns far faster than a restore point is worth, so
exclude the workspace's dataset from hourly snapshots on shem right after the
first start. The dataset name exists only once the PVC has been created.

### Launch link

Once the storage configuration is provisioned, select the shem devfile with
Che's `devfilePath` URL parameter, naming a branch that carries it:

```text
https://code.ethosengine.com/#https://github.com/ethosengine/elohim/tree/<branch>?devfilePath=devfile-shem.yaml
```

The devfile was introduced on the `infra/devfile-shem` branch on 2026-10-01.
Use `dev` once it has been merged there.

The link selects the devfile; placement is specified inside
that file. To make ordinary new Elohim launches default to shem instead, put
the shem attributes in the root `devfile.yaml` once the storage setup is ready.
An explicit LAN variant can then be selected with `devfilePath`.

This applies to a **new workspace with a new PVC**. An existing LAN workspace
does not move its data when its selector changes. Start a fresh workspace or
have ops migrate the data and claim. Keep `/projects`, build targets, local
databases and caches on shem; never attach LAN Jiva or NFS volumes to this
workspace. Separate workspace PVCs isolate files, but CPU, memory and disk
bandwidth are still shared with the other workloads on shem.

Before treating a launched workspace as ready, verify its
configuration reference, its PVC's `storageClassName: shem-zfs`, and its pod
and PV placement on shem. Confirm `/projects` persists across a stop/start.

References: [DevWorkspace configuration and pod overrides](https://github.com/devfile/devworkspace-operator/blob/main/docs/additional-configuration.adoc),
[workspace storage configuration API](https://github.com/devfile/devworkspace-operator/blob/main/apis/controller/v1alpha1/devworkspaceoperatorconfig_types.go),
[Che-owned configuration](https://eclipse.dev/che/docs/stable/discover/devworkspace-operator/),
and [launch-link devfile selection](https://eclipse.dev/che/docs/stable/end-user-guide/url-parameter-for-the-devfile-file-path/).
