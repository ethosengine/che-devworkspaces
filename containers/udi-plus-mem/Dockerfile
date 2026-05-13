# podman build --pull --no-cache -t harbor.ethosengine.com/devspaces/udi-plus-mem:latest .
# podman push harbor.ethosengine.com/devspaces/udi-plus-mem:latest

# ============================================================================
# udi-plus + MemPalace (semantic memory tooling) for Eclipse Che/DevSpaces
# ============================================================================
#
# Layered on udi-plus so the base image stays slim for workspaces that don't
# need ML deps. This layer adds:
#   - MemPalace (semantic memory MCP)
#   - ChromaDB (vector store backing MemPalace)
#   - sentence-transformers + CPU-only torch (~1.5GB)
#   - Pre-warmed all-MiniLM-L6-v2 embedding model (~90MB) baked at /opt/mempalace/hf-cache
#
# Offline-by-default: HF_HUB_OFFLINE / TRANSFORMERS_OFFLINE are set as ENV so
# first workspace start cannot phone home to Hugging Face Hub.
#
# Arbitrary-UID notes (Che requirement):
#   - /opt/mempalace is created chown user:root, chmod g=u (mirrors /opt/mcp)
#   - HF_HOME points to /opt/mempalace/hf-cache, NOT ~/.cache/huggingface,
#     so model weights survive PVC mounts on /home/user
# ============================================================================

ARG BASE_TAG=latest
FROM harbor.ethosengine.com/devspaces/udi-plus:${BASE_TAG}

USER root

# Hugging Face cache location — outside /home/user so PVC mounts don't shadow it.
# Set BEFORE the warm-up RUN so weights land here at image build time.
ENV HF_HOME=/opt/mempalace/hf-cache
ENV SENTENCE_TRANSFORMERS_HOME=/opt/mempalace/hf-cache/sentence-transformers
ENV MEMPALACE_HOME_DEFAULT=/opt/mempalace
# NOTE: HF Hub warm-up does NOT route through Nexus. The huggingface_hub
# client verifies the response with custom headers (X-Repo-Commit,
# X-Linked-Etag) that Nexus OSS raw-proxy strips during redirect-follow.
# Setting HF_ENDPOINT=https://nexus.../huggingface-proxy fails with
# "Distant resource does not seem to be on huggingface.co". Fixing this
# requires Nexus Pro header-passthrough config (not OSS-available).
# Build-time warmup happens once per image rebuild, well under HF's
# anonymous rate limit. At runtime HF_HUB_OFFLINE=1 blocks all HF traffic.

# Layout under /opt/mempalace:
#   hf-cache/          — Hugging Face model + tokenizer cache (read-only at runtime)
#   etc/               — bundled config templates (read-only at runtime)
#   bin/               — wrapper scripts
RUN mkdir -p /opt/mempalace/hf-cache \
             /opt/mempalace/etc \
             /opt/mempalace/bin \
 && chown -R user:root /opt/mempalace \
 && chmod -R g=u /opt/mempalace

# ============================================================================
# Python deps
# ============================================================================
# pip is bootstrapped by udi-plus (line 77 of udi-plus/Dockerfile). We reuse
# the same `python3 -m pip` invocation pattern for consistency.
#
# CPU-only torch is installed from the PyTorch CPU wheel index FIRST so the
# resolver does not pull the much larger CUDA build as a transitive dep of
# sentence-transformers.
#
# Versions below were verified against canonical sources at build authoring time:
#   torch                  — pypi.org/pypi/torch/json + github.com/pytorch/pytorch
#   sentence-transformers  — pypi.org/pypi/sentence-transformers/json
#                            + github.com/UKPLab/sentence-transformers
#   chromadb               — pypi.org/pypi/chromadb/json
#                            + github.com/chroma-core/chroma
# Re-verify against those same canonical sources before bumping.
ARG TORCH_VERSION=2.12.0
ARG SENTENCE_TRANSFORMERS_VERSION=5.5.0
ARG CHROMADB_VERSION=1.5.9

# NOTE: torch CPU wheels still pull from download.pytorch.org directly, NOT
# via Nexus. Two structural reasons make raw-proxying the pytorch CPU index
# infeasible without an HTML-rewriting frontend:
#   1. The flat index returns absolute-rooted hrefs (e.g. /whl/setuptools-...)
#      that lose the /repository/pytorch-cpu-proxy/ prefix when pip resolves
#      them against Nexus's subpath URL, 404-ing every transitive dep.
#   2. Wheel hrefs in the index point at download-r2.pytorch.org (a separate
#      CDN host), so a Nexus proxy on download.pytorch.org only caches index
#      HTML, never the wheel blobs.
# download-r2 has generous rate limits; revisit if it ever bites.
RUN python3 -m pip install --no-cache-dir \
      --index-url https://download.pytorch.org/whl/cpu \
      "torch==${TORCH_VERSION}"

RUN python3 -m pip install --no-cache-dir \
      --index-url https://nexus.ethosengine.com/repository/pypi-proxy/simple/ \
      "sentence-transformers==${SENTENCE_TRANSFORMERS_VERSION}" \
      "chromadb==${CHROMADB_VERSION}"

# ============================================================================
# MemPalace
# ============================================================================
# Canonical source: github.com/MemPalace/mempalace (confirmed by user).
# Published to PyPI as "mempalace"; the v3.3.5 tag on the GitHub repo matches
# the PyPI release of the same version, so the PyPI artifact is the project's
# own publication. Pin version explicitly — do NOT install unpinned.
ARG MEMPALACE_VERSION=3.3.5
RUN python3 -m pip install --no-cache-dir \
      --index-url https://nexus.ethosengine.com/repository/pypi-proxy/simple/ \
      "mempalace==${MEMPALACE_VERSION}"

# ============================================================================
# Pre-warm the embedding model so first workspace start is offline-clean.
# all-MiniLM-L6-v2 is ~90MB; chosen as a small, well-supported default.
# Override with --build-arg EMBEDDING_MODEL=... to bake a different model.
# ============================================================================
ARG EMBEDDING_MODEL=sentence-transformers/all-MiniLM-L6-v2
RUN python3 -c "from sentence_transformers import SentenceTransformer; \
                SentenceTransformer('${EMBEDDING_MODEL}')" \
 && chown -R user:root /opt/mempalace/hf-cache \
 && chmod -R g=u /opt/mempalace/hf-cache

# Now lock down to fully offline — applies at workspace runtime, not build time.
ENV HF_HUB_OFFLINE=1
ENV TRANSFORMERS_OFFLINE=1

# ============================================================================
# Verify install (build fails if any binary is missing)
# ============================================================================
RUN python3 -c "import torch, sentence_transformers, chromadb; \
                print('torch', torch.__version__); \
                print('sentence_transformers', sentence_transformers.__version__); \
                print('chromadb', chromadb.__version__)"

# Verify the mempalace CLI is on PATH. If the package exposes a different
# console_scripts entry point, this RUN will fail the build — update the
# command name to whatever pyproject.toml declares.
RUN mempalace --version

USER user
WORKDIR /projects
