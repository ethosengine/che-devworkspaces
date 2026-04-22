# rust-dev Container Notes

Context for maintainers of `Dockerfile`. Expected to be revisited when
holochain/datachannel-sys versions bump or the upstream libdatachannel fix
lands.

## Why cmake, zlib-devel, and the CMakeLists patch all exist

Holochain's default features pull `datachannel-sys`, which vendors libdatachannel
and a vendored OpenSSL build. Building it end-to-end requires three things UBI10
does not provide out of the box, layered in this order:

1. **`cmake`** — libdatachannel's build.rs invokes cmake. Without it, the build
   fails at configure. Added via dnf.
2. **`zlib-devel`** — datachannel-sys's vendored OpenSSL exports a CMake config
   that declares `ZLIB::ZLIB` in its link interface. The runtime `libz.so.1` is
   present in UBI10, but `zlib.h`, the `libz.so` symlink, and `zlib.pc` are not.
   Without them the configure step cannot resolve the link. Added via dnf.
3. **CMakeLists sed patch** — even with both libs installed, libdatachannel's
   own `CMakeLists.txt` calls `find_package(OpenSSL REQUIRED)` without first
   calling `find_package(ZLIB REQUIRED)`. The vendored OpenSSL's config then
   references a `ZLIB::ZLIB` target that does not exist yet, and cmake errors
   out. This is a source-level bug in libdatachannel, not something dnf can
   fix. The two `RUN` blocks after the Rust verify step prime the cargo
   registry and inject `find_package(ZLIB REQUIRED)` on the line above the
   existing OpenSSL call.

All three layers are required. Removing any one of them re-breaks the build.

## Exit conditions

- **Upstream fix**: when https://github.com/paullouisageneau/libdatachannel
  adds `find_package(ZLIB REQUIRED)` before `find_package(OpenSSL REQUIRED)`
  in `CMakeLists.txt`, and datachannel-sys picks up a release containing it,
  delete both patch `RUN` blocks. The sed's `grep -q 'find_package(ZLIB REQUIRED)'`
  guard would trip and print `already patched` anyway, but there is no reason
  to keep dead code.
- **Datachannel feature swap**: elohim could switch holochain to
  `backend-go-pion` and drop the C++ datachannel path entirely. If that
  happens the sed block goes, but `cmake` and `zlib-devel` should stay — they
  are cheap and other native Rust deps use them.

## Updating the patch on a datachannel-sys version bump

The version string appears in two places inside the Dockerfile and must match:

1. `cargo add --quiet datachannel-sys@=0.23.0` — pins the crate to fetch.
2. `find -path '*datachannel-sys-0.23.0+0.23.2/...'` — the directory name
   cargo unpacks the crate into. Note the `+0.23.2` suffix is the upstream
   libdatachannel version datachannel-sys vendors; it is NOT the crate version
   and will not always match the `cargo add` pin.

When bumping:
1. Run `cargo add datachannel-sys@<new>` in a scratch project locally, look at
   `ls ~/.cargo/registry/src/index.crates.io-*/datachannel-sys-*` to learn the
   full unpacked dir name (crate version + `+` + libdatachannel version), and
   update the `-path` pattern to match.
2. Open the unpacked `libdatachannel/CMakeLists.txt` and confirm the target
   line is still exactly `\tfind_package(OpenSSL REQUIRED)` (one leading tab).
   If indentation or wording changed, update the `sed` expression.
3. Re-run the scoped validation below.

The patch's `find -print -quit` with hard-fail on empty match is deliberate:
a silent version drift would produce a container that builds but still breaks
sweettest. Loud failure at image-build time is the correct rot-proofing.

## Why not a cargo `[patch.crates-io]` git fork

That approach requires two forks on our GH org (libdatachannel + datachannel-rs
with submodule repointed) because libdatachannel is a git submodule upstream.
The cargo-registry tarball flattens the submodule into regular files, so the
Dockerfile-level sed works on a single inlined path with no fork maintenance.
If we ever need sweettest to build on bare laptops (no rust-nix-dev image),
revisit — the fork is the correct shape for that case. Today, Che and Jenkins
are the sanctioned environments, so the image-level patch is the lower-tax
option.

## Why `cargo add`, not `printf >> Cargo.toml`

Modern `cargo init` writes an empty `[dependencies]` section into Cargo.toml.
Appending another `[dependencies]` header with `printf` produces a duplicate
TOML key and cargo refuses to parse. Use `cargo add <crate>@=<version>` — it
edits the manifest via the TOML writer rather than text concatenation. Caught
during initial validation; the earlier version of this patch would not have
built.

## Validating the patch without a full udi-plus rebuild

A full rust-dev build is ~20 minutes. To iterate on just the cargo-fetch and
sed logic, run the two RUN blocks against a public `rust` base via buildctl:

```bash
# In /tmp/dc-validate/Dockerfile
FROM rust:1-slim-bookworm
RUN mkdir -p /tmp/fetch && cd /tmp/fetch && \
    cargo init --quiet --name fetch-only && \
    cargo add --quiet datachannel-sys@=0.23.0 && \
    cargo fetch --quiet && rm -rf /tmp/fetch
RUN set -e; \
    CML=$(find /usr/local/cargo/registry/src \
            -path '*datachannel-sys-<version>+<libdc-version>/libdatachannel/CMakeLists.txt' \
            -print -quit); \
    echo "CML=$CML"; \
    grep -n 'find_package(OpenSSL REQUIRED)' "$CML"
```

Note the CARGO_HOME path: rust official image uses `/usr/local/cargo`,
rust-dev uses `/opt/rust/cargo`. Adjust the `find` root accordingly when
porting the real Dockerfile snippet over to scratch validation.

```bash
buildctl --addr unix:///run/buildkit/buildkitd.sock build \
  --frontend dockerfile.v0 \
  --local context=/tmp/dc-validate \
  --local dockerfile=/tmp/dc-validate \
  --progress=plain
```

Takes ~2 minutes. If it passes there, the same steps will pass in the real
rust-dev build (the only meaningful difference is CARGO_HOME path).

## Does Jenkins ci-builder-nix need the same patch?

Nixpkgs provides cmake and zlib via the dev shell, so the first two layers
are automatically covered. The CMakeLists source bug is NOT covered — nix
does not patch source tarballs cargo downloads from crates.io. If Jenkins
ever runs `cargo test -p sweettest` inside `nix develop` without the
holochain-ci cachix cache supplying a pre-built artifact, it will hit the
identical failure. Options when that pipeline lands:

- Mirror this sed patch into `containers/ci-builder/ci-builder-nix/Dockerfile`
  (post-`cargo fetch` in a priming step, same pattern).
- Configure holochain's flake to use `holochain-ci.cachix.org` so cargo reuses
  cached libdatachannel artifacts instead of recompiling.
- Land the upstream libdatachannel PR, after which neither environment needs
  a patch.

No sweettest pipeline exists today, so this is a note for the next person to
wire one up, not a current production blocker.
