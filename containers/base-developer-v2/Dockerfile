#podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/base-developer-v2:latest .
#podman push harbor.ethosengine.com/devspaces/base-developer-v2:latest

# ============================================================================
# base-developer-v2 — the Che base developer image on an x86-64-v2 userland
# ============================================================================
#
# WHY THIS EXISTS: the RHEL/UBI 10 userland is compiled for x86-64-v3, so
# nothing in it starts on a CPU without AVX2 (shem: Xeon E5-2667, Sandy Bridge,
# x86-64-v2). A Che workspace placed there died in its first container with
# "Fatal glibc error: CPU does not support x86-64-v3" (2026-10-01).
# UBI 9 is not an escape: its libstdc++ stops at GLIBCXX_3.4.29 and the
# pre-built Holochain binaries need GLIBCXX_3.4.30.
#
# AlmaLinux 10 publishes an x86-64-v2 build of the EL10 userland (GCC 14, dnf,
# same package names). Verified 2026-10-01: it exists on quay ONLY (not Docker
# Hub), selected with platform linux/amd64/v2. Plan:
# docs/2026-10-01-x86-64-v2-workspace-image-plan.md (step 1).
#
# WHAT THIS IS: the upstream devfile/developer-images base/ubi10 Dockerfile,
# reproduced by hand on that userland. Architecture-level-independent files
# (shell scripts, the stow source tree, static Go/Rust binaries) are GRAFTED from
# the upstream image in the `upstream` stage rather than vendored here, so they
# track upstream. The upstream image is never executed. Everything dynamically
# linked comes from AlmaLinux's own v2 packages.
#
# EPEL: enabled with AlmaLinux's own `epel-release` package (AlmaLinux rebuilds
# EPEL for v2). The Fedora epel-release-latest-10 URL would configure v3-built
# packages and must not be used here.
#
# BUILT LOCALLY 2026-10-01 (BuildKit v0.12.5, not yet by Jenkins, not yet run on
# shem). That build confirmed: Harbor's proxy-quay serves the v2 manifest and
# BuildKit honours the platform pin (every arch package is x86_64_v2, libc's ISA
# note needs only v2); the whole dnf list resolves, stow included, from
# AlmaLinux's own repos; /home/tooling matches the upstream image.
# The final RUN is a build-host check only. The real proof is the smoke gate on
# shem (containers/smoke/v2-smoke.sh), because this build host supports v3 and
# so cannot catch a userland that REQUIRES v3.
# ============================================================================

# Source of architecture-independent files only. Never executed.
FROM harbor.ethosengine.com/proxy-quay/devfile/base-developer-image:ubi10-latest AS upstream

# The platform pin below is the point of this image.
# hadolint ignore=DL3029
FROM --platform=linux/amd64/v2 harbor.ethosengine.com/proxy-quay/almalinuxorg/almalinux:10

USER root

# EPEL (AlmaLinux's v2 rebuild) plus CRB, which several EPEL packages depend on.
RUN dnf install -y dnf-plugins-core epel-release && \
    dnf config-manager --set-enabled crb && \
    dnf clean all

# Same package set as upstream's base/ubi10 first RUN, plus python3 (the udi-plus
# layer's `python3 -m ensurepip` relies on the upstream base carrying it),
# shadow-utils (useradd) and binutils (readelf, used by the assertion at the end).
RUN dnf install -y \
    diffutils git git-lfs iproute jq less lsof man-db nano procps \
    p7zip p7zip-plugins perl-Digest-SHA net-tools openssh-clients openssl \
    ripgrep rsync socat sudo time vim wget zip \
    python3 shadow-utils binutils && \
    dnf clean all

# stow is a noarch Perl package. Try the enabled repos first; fall back to a
# temporary EPEL 9 repo. Its URL hardcodes x86_64 instead of $basearch because
# on this userland $basearch may expand to x86_64_v2, which EPEL 9 mirrors lack.
RUN dnf install -y stow || { \
        printf '%s\n' \
            '[epel9-tmp]' \
            'name=EPEL 9 (temporary, for stow)' \
            'baseurl=https://dl.fedoraproject.org/pub/epel/9/Everything/x86_64/' \
            'enabled=0' \
            'gpgcheck=1' \
            'gpgkey=https://dl.fedoraproject.org/pub/epel/RPM-GPG-KEY-EPEL-9' \
            > /etc/yum.repos.d/epel9-tmp.repo && \
        dnf install -y --enablerepo=epel9-tmp stow; \
        rc=$?; \
        rm -f /etc/yum.repos.d/epel9-tmp.repo; \
        exit $rc; \
    } && \
    dnf clean all

# Container tooling for in-workspace image builds (rootless, via the wrapper below).
RUN dnf install -y podman buildah skopeo fuse-overlayfs && \
    dnf clean all

# kubedock_setup and anything else upstream ships under /usr/local do not depend
# on the OS userland. Checked 2026-10-01: the published ubi10 image carries ONLY
# kubedock_setup there (its gh/rg/bat/fd/kubedock downloads are skipped at build
# time), so this image has no kubedock binary either, same as upstream.
# Copying the tree tolerates whatever exact set upstream ships; the assertion at
# the end rejects any dynamic binary in it that is missing a library here.
COPY --from=upstream /usr/local/ /usr/local/

# Version pin the upstream image exports (0.18.2 as of 2026-10-01); kubedock_setup
# reads it. If upstream starts shipping the binary, the build warns on a mismatch.
ARG KUBEDOCK_VERSION=0.18.2
ENV KUBEDOCK_VERSION=${KUBEDOCK_VERSION}
RUN if command -v kubedock >/dev/null 2>&1; then \
        kubedock version 2>&1 | grep -q "${KUBEDOCK_VERSION}" || \
        echo "WARNING: kubedock version does not mention ${KUBEDOCK_VERSION}; fix ARG KUBEDOCK_VERSION"; \
    fi

ENV PATH="/home/user/.local/bin:$PATH"
ENV KUBECONFIG=/home/user/.kube/config

# Rootless buildah inside the workspace container.
ENV _BUILDAH_STARTED_IN_USERNS="" BUILDAH_ISOLATION=chroot

# Subordinate ids for the workspace user; /etc/passwd and /etc/group are made
# group-writable below so Che's arbitrary UID can be added at start.
RUN touch /etc/subgid /etc/subuid && \
    chmod g=u /etc/subgid /etc/subuid && \
    echo user:10000:65536 > /etc/subuid && \
    echo user:10000:65536 > /etc/subgid

# Containers storage: shared read-only image store, fuse-overlayfs mounts.
# EL10's containers-common ships the file under /usr/share, not /etc.
RUN sed -i -e 's|^#mount_program|mount_program|g' \
           -e '/additionalimage.*/a "/var/lib/shared",' \
           -e 's|^mountopt[[:space:]]*=.*$|mountopt = "nodev,fsync=0"|g' \
           /usr/share/containers/storage.conf && \
    grep -q '^mount_program' /usr/share/containers/storage.conf && \
    mkdir -p /var/lib/shared/overlay-images /var/lib/shared/overlay-layers \
             /var/lib/shared/vfs-images /var/lib/shared/vfs-layers && \
    touch /var/lib/shared/overlay-images/images.lock \
          /var/lib/shared/overlay-layers/layers.lock \
          /var/lib/shared/vfs-images/images.lock \
          /var/lib/shared/vfs-layers/layers.lock

# podman wrapper: the real binary moves aside, the entrypoint wires the wrapper in.
COPY --from=upstream --chown=0:0 /usr/bin/podman.wrapper /usr/bin/podman.wrapper
ENV PODMAN_WRAPPER_PATH=/usr/bin/podman.wrapper
ENV ORIGINAL_PODMAN_PATH=/usr/bin/podman.orig
RUN mv /usr/bin/podman /usr/bin/podman.orig

COPY --from=upstream --chown=0:0 /entrypoint.sh /entrypoint.sh

# stow source tree, including dotfiles (.stow-local-ignore, .copy-files).
# The three files removed below are post-stow copies in the upstream image
# (recreated after stow); left in place they would collide with the links.
COPY --from=upstream /home/tooling/ /home/tooling/
RUN rm -f /home/tooling/.viminfo /home/tooling/.bashrc /home/tooling/.bash_profile

# Workspace user (Che runs as 10001 unless the platform overrides the UID).
RUN useradd -u 10001 -G wheel,root -d /home/user --shell /bin/bash -m user && \
    echo 'user ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/user && \
    chmod 0440 /etc/sudoers.d/user && \
    mkdir -p /home/user/.kube /home/user/.local/share/containers && \
    chown -R 10001:0 /home/user /home/tooling && \
    chmod -R g=u /home/user /home/tooling && \
    chmod g=u /etc/passwd /etc/group

# Consistent prompt: working directory plus current git branch.
RUN printf '%s\n' 'export PS1="\W \$(git branch --show-current 2>/dev/null | sed -r -e \"s@^(.+)@(\1) @\")\$ "' \
      > /etc/profile.d/prompt.sh

USER 10001
ENV HOME=/home/user

# Link the stow tree into the home, then keep copies of the files the
# entrypoint restores after a PVC mount shadows /home/user (see .copy-files).
RUN stow . -t /home/user/ -d /home/tooling/ && \
    touch /home/user/.viminfo && \
    cp -n /home/user/.viminfo /home/tooling/.viminfo && \
    cp -n /home/user/.bashrc /home/tooling/.bashrc && \
    cp -n /home/user/.bash_profile /home/tooling/.bash_profile

USER root
RUN chgrp -R 0 /home && chmod -R g=u /home

# Build-host sanity check; fails the build on an obviously wrong userland.
#   - ld.so must know x86-64-v2 (the v3-only RHEL userland dies before it can
#     print anything on a v2 host, but this host supports v3, so "v3 supported"
#     appears too and proves nothing about requirements).
#   - libstdc++ must carry GLIBCXX_3.4.30 (Holochain).
#   - If readelf can read the ISA note, libc and bash must not NEED v3 or v4:
#     this is the one check here that is independent of the build host's CPU.
#   - Every dynamic binary grafted from upstream must resolve its libraries.
# The real proof that the image starts on shem is the "Smoke on shem" gate
# (containers/smoke/v2-smoke.sh), not this RUN.
RUN set -eu; \
    /lib64/ld-linux-x86-64.so.2 --help | grep -q 'x86-64-v2' \
        || { echo "ld.so does not list x86-64-v2"; exit 1; }; \
    grep -aq 'GLIBCXX_3.4.30' /usr/lib64/libstdc++.so.6 \
        || { echo "libstdc++ lacks GLIBCXX_3.4.30"; exit 1; }; \
    if command -v readelf >/dev/null 2>&1; then \
        if readelf -n /usr/lib64/libc.so.6 /usr/bin/bash 2>/dev/null | grep -E 'ISA needed' | grep -Eq 'x86-64-v[34]'; then \
            echo "userland requires x86-64-v3 or newer"; exit 1; \
        fi; \
    fi; \
    for f in /usr/local/bin/*; do \
        if ldd "$f" 2>&1 | grep -q 'not found'; then \
            echo "grafted binary has unresolved libraries: $f"; ldd "$f"; exit 1; \
        fi; \
    done; \
    test -x /usr/libexec/podman/catatonit; \
    test -x /usr/bin/podman.orig; \
    test -x /entrypoint.sh; \
    echo "base-developer-v2 build-host checks passed"

USER 10001
WORKDIR /projects
ENTRYPOINT ["/usr/libexec/podman/catatonit","--","/entrypoint.sh"]
CMD ["tail","-f","/dev/null"]
