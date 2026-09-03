# syntax=docker/dockerfile:1
# ─────────────────────────────────────────────────────────────────────────────
# Custom edgenode carrying the Elohim Holochain 0.7 conductor fork.
#
# HISTORY: the 0.6 image carried the tx5/go-pion zombie-PeerConnection leak fix.
#   Holochain 0.7 removes that transport; iroh is now the only transport.
#
# HOW: holo-host's base downloads a PREBUILT holochain to /bin/holochain. We build OUR
#   patched conductor and swap it in. Pure binary swap — no need to replicate holo-host's
#   whole image build.
#
# HC_FEATURES is the Holochain 0.7 production set. Canary the resulting conductor on
# one non-genesis peer and confirm gossip/DHT health before a genesis-pair or fleet roll.
#
# BUILD (heavy — CI/build-host: ~20-30 GB target, ~30 min; needs network for crates):
#   docker build -f elohim/holochain/edgenode/Dockerfile.zombie-fix \
#     -t <registry>/elohim-edgenode:zombie-fix elohim/holochain/edgenode
# ─────────────────────────────────────────────────────────────────────────────

ARG HC_FORK=https://github.com/ethosengine/holochain.git
ARG HC_BRANCH=elohim-0.7
# HC_REF builds an EXACT commit instead of a branch tip. The elohim monorepo passes
# the SHA its `elohim/holochain-conductor` submodule pointer records, so the image
# identity is derived from committed source rather than "whatever the branch tip was
# at build time". Empty (the default) falls back to the branch clone for manual builds.
# This also closes a cache hole: a branch-tip clone layer can be reused across
# two different tips, whereas a REF change always invalidates it.
ARG HC_REF=
# `jemalloc` remains our fork's production allocator cure; `schema` is omitted.
ARG HC_FEATURES=encryption,wasmer-sys-cranelift,jemalloc

# ── builder: compile the patched glibc holochain binary ──────────────────────
FROM rust:1.91-bookworm AS build
ARG HC_FORK HC_BRANCH HC_FEATURES HC_REF
RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential cmake perl pkg-config libssl-dev ca-certificates curl git \
 && rm -rf /var/lib/apt/lists/*
ENV RUSTFLAGS=""
WORKDIR /src
# HC_REF (exact commit) wins over the branch when supplied — fetched shallow by SHA,
# which GitHub serves (uploadpack.allowAnySHA1InWant). The fetched commit is echoed so
# the build log records exactly which source produced the binary.
COPY clone-fork.sh /usr/local/bin/clone-fork.sh
RUN chmod +x /usr/local/bin/clone-fork.sh \
 && clone-fork.sh "${HC_FORK}" "${HC_BRANCH}" "${HC_REF}" holochain-conductor
WORKDIR /src/holochain-conductor
# --no-default-features drops `schema`; encryption, Cranelift, and the allocator cure are
# selected explicitly. Iroh is the only 0.7 transport and has no cargo feature.
RUN cargo build --release --bin holochain \
      --no-default-features --features "${HC_FEATURES}"

# ── final: holo-host base, binary swapped ────────────────────────────────────
# Byte-identical to the previous runtime base tag, pinned by its amd64 manifest digest.
FROM ghcr.io/holo-host/edgenode@sha256:f1c010225af0a8f4750ed516ecf2388a2637a66d123df167b82f979032ac8744
# /bin/holochain is where the base image's prebuilt conductor lives (entrypoint launches it)
COPY --from=build /src/holochain-conductor/target/release/holochain /bin/holochain
# the elohim conductor config (same as the stock Dockerfile)
COPY conductor-config.yaml /etc/holochain/conductor-config.yaml
ENV CONDUCTOR_MODE=true
EXPOSE 4444 4445
# entrypoint inherited from base: /usr/local/bin/entrypoint.sh → /bin/holochain
