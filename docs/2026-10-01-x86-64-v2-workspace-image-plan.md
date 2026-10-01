# Workspace images that run on x86-64-v2 hosts (shem)

**Status:** steps 1, 2 and 3 are written and build locally (2026-10-01, BuildKit v0.12.5 on
a v3-capable host): all four layers build and the smoke script passes inside each. Nothing has
been built by Jenkins, pushed, or run on shem, so step 5's gate is written but unexercised.
Step 0 is closed by a verified fact (below). Steps 4 (running the smoke on shem), 6, 7 and 8
are open. **Date:** 2026-10-01.

## Problem

A Che workspace launched on shem on 2026-10-01 failed at its first container
(`init-persistent-home`) with exit code 127 and:

```text
Fatal glibc error: CPU does not support x86-64-v3
```

shem's CPUs are Xeon E5-2667 (Sandy Bridge). They have AVX but not AVX2, BMI1/2 or FMA, which
makes them x86-64-v2. The dev image chain is built on `base-developer-image:ubi10-latest`, and
the RHEL/UBI 10 userland is compiled for x86-64-v3, so nothing in the image can start there.

Placement and storage were not the cause. The pod scheduled on shem, the 220Gi `shem-zfs`
volume bound, and the project clone completed onto it.

## The image chain today

```text
quay.io/devfile/base-developer-image:ubi10-latest
  -> udi-plus               containers/udi-plus/Dockerfile:14
    -> udi-plus-mem         containers/udi-plus-mem/Dockerfile:25        (ARG BASE_TAG)
      -> udi-plus-mem-rust-nix  containers/udi-plus-mem-rust-nix/Dockerfile:29  (ARG BASE_TAG)
```

`udi-plus` has no base argument. `jenkins/Jenkinsfile-udi-plus` cascades to the downstream
image jobs; `jenkins/Jenkinsfile-udi-plus-mem-rust-nix` takes a `BASE_TAG` parameter and passes
it as a build argument.

Pre-built binaries pulled into the chain: the Node 24 tarball, `mongod` 8.0 (`rhel93` build),
kubeconform, hadolint, gh, nerdctl and buildkit, sccache (musl), the rustup toolchain,
Holochain 0.7.0 (`holochain`, `hc`, `hcterm`), torch CPU wheels, chromadb and
sentence-transformers.

## The constraint that rules out the obvious fix

Rebasing on UBI 9 would give an x86-64-v2 userland, but UBI 9 ships GCC 11, whose libstdc++
stops at `GLIBCXX_3.4.29`. The pre-built Holochain binaries need `GLIBCXX_3.4.30`; that is the
reason UBI 10 was chosen in the first place (`containers/udi-plus/Dockerfile:4`,
`containers/udi-plus-mem-rust-nix/Dockerfile:144`).

The base therefore has to satisfy both conditions: x86-64-v2, and GCC 12 or newer.

## Recommended approach: an EL10-compatible userland built for x86-64-v2

AlmaLinux 10 publishes an x86-64-v2 build of the EL10 userland: same package names, GCC 14,
`dnf`. Rebuilding the bottom of the chain on it keeps every `dnf install` line and every
EL-specific workaround in these Dockerfiles unchanged.

Verified 2026-10-01: the image exists on quay only, not on Docker Hub, as
`quay.io/almalinuxorg/almalinux:10`, selected with platform `linux/amd64/v2`; through the
Harbor proxy that is `harbor.ethosengine.com/proxy-quay/almalinuxorg/almalinux:10`. What is
still unverified is that BuildKit v0.12.5 and the Harbor proxy preserve the v2 manifest, that
EPEL resolves from v2 repositories, and the rest of step 0's list; the first build and the
smoke on shem check them.

**Fallback if step 0 fails:** a Debian 13 base (baseline x86-64, GCC 14). It satisfies both
conditions, but the `dnf` steps become `apt` and the Che base-image conventions have to be
reproduced by hand. More drift, so it is the second choice.

## Steps

0. **Spike (about half a day, no repository changes).** On shem, run the candidate base and
   print the supported levels:

   ```bash
   /lib64/ld-linux-x86-64.so.2 --help | grep x86-64-v
   ```

   Confirm that the v2 image exists and pulls through the Harbor proxy, that `dnf` resolves
   from v2 repositories including EPEL, and that
   `strings /usr/lib64/libstdc++.so.6 | grep GLIBCXX_3.4.30` finds the symbol version. If any
   of these fails, stop and switch to the fallback.

1. **Own the base layer.** Add `containers/base-developer-v2/Dockerfile`: the upstream
   `devfile/developer-images` base Dockerfile for ubi10 with its `FROM` swapped to the v2
   userland. This is the one new image to maintain. It carries the Che entrypoint, the
   arbitrary-UID home handling and checode compatibility.
   *Written, unbuilt.* It reproduces the upstream RUN steps with dnf on AlmaLinux 10 (EPEL
   from AlmaLinux's own `epel-release`, which is rebuilt for v2) but GRAFTS the
   architecture-independent files from the upstream image instead of vendoring them:
   `entrypoint.sh`, the `/home/tooling` stow tree, `podman.wrapper`, and the static tools
   and `kubedock_setup` under `/usr/local`. The upstream image is a build input only and is
   never executed. Job: `jenkins/Jenkinsfile-base-developer-v2`.

2. **Parameterise `udi-plus`.** Add `ARG BASE_IMAGE`, defaulting to today's UBI 10 base so the
   existing image is unaffected. Make no other change to that Dockerfile. *Written.*

3. **Build a parallel chain by tag.** Use the same three Dockerfiles with a `v2-latest` tag
   (plus the dated and git tags used today):
   `udi-plus:v2-latest` -> `udi-plus-mem:v2-latest` -> `udi-plus-mem-rust-nix:v2-latest`,
   through the `BASE_TAG` arguments that already exist. Add a `VARIANT` parameter to the three
   Jenkinsfiles; do not fork them. The default cascade stays on `latest`. *Written*: the v2
   tags are `v2-latest`, `v2-<date>`, `v2-<git>`, so nothing in a v2 run can move `latest`.

4. **Check each pre-built binary on shem.** One smoke script, run in a container on shem:

   ```bash
   node --version
   mongod --version          # MongoDB 5+ needs AVX; Sandy Bridge has it
   holochain --version && hc --version
   rustc --version && sccache --version
   python3 -c "import torch, chromadb, sentence_transformers"
   claude --version && codex --version
   java -version
   # plus a headless start of Chrome for Testing
   ```

   Any binary that dies with SIGILL gets a pinned baseline build, or is dropped from the v2
   variant.

5. **Make the smoke a gate.** Add a Jenkins stage with
   `nodeSelector: kubernetes.io/hostname: shem` and the `remote-wan` toleration that runs the
   smoke script against the freshly built v2 tag. Promote the tag to `v2-latest` only if it
   passes. This is the check that would have caught the 2026-10-01 failure.
   *Written.* The build pushes only the dated and git tags; `smokeAndPromoteDevspaceImage`
   runs the smoke on shem and then adds `v2-latest` with a Harbor retag.

6. **Workspace-side artefacts.** Before first real use on shem:
   - confirm nothing in the elohim repository sets `target-cpu=native` (cargo config or
     `RUSTFLAGS`); such artefacts would not be portable between nodes through the shared
     sccache;
   - build the pinned conductor fork on shem itself;
   - let `pnpm install` fetch its own native modules there.

7. **Point the shem devfile at the tag.** In the elohim repository, set `devfile-shem.yaml` to
   `udi-plus-mem-rust-nix:v2-latest`. Launch, then verify the PVC class, the pod placement and
   a full `just gate` of one small project.

8. **Decide on one image or two.** A v2 userland runs on both nodes. After two weeks of use on
   shem, either make v2 the single image (one chain to maintain, a small CPU-feature cost on
   ethosengine) or keep both. Not decided here.

## Costs and risks

- One more base image to maintain, tracking upstream `developer-images` by hand.
- A v2 chain roughly doubles image build time and registry storage while both exist.
- shem remains a slow, shared build host. It has 24 threads of 2012-era hardware, the fleet
  peers were using about 9 of them on 2026-10-01, and `devfile-shem.yaml` caps the workspace
  at 8. This plan fixes "cannot start", not "builds are slow".

## Cluster state left from the 2026-10-01 attempt

Operator-owned; listed so it is not forgotten:

- the failed `elohim-devspace-shem` workspace and its retained 220Gi dataset (`shem-zfs` is
  `Retain`, so the PV, zfsvolume and dataset need removing by hand);
- `devworkspace-config-shem` in `eclipse-che` (keep: it is harmless and needed by step 7);
- the CheCluster selector, changed to `che-workspaces=true`. Unless this plan proceeds, revert
  it before the operator reconciles, to avoid restarting `elohim-devspace`.

## Out of scope

- The fleet conductor and storage images. They already run on shem.
- Snapshot policy for workspace datasets, covered in the README.
