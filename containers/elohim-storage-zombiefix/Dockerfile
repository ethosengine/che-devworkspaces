# Canary elohim-storage image — production storage service + PATCHED conductor.
#
# FAST binary swap (no Rust rebuild, no apt): the live alpha runs the consolidated
# `elohim-node` container = the elohim-storage image, which spawns the holochain
# conductor as an embedded child from /usr/local/bin/holochain. The storage SERVICE
# is unchanged; only the conductor leaks. So we take the production storage image and
# overlay the patched conductor binary from our custom edgenode image.
#
#   STORAGE_BASE     = the current production elohim-storage tag to base on (unchanged service)
#   CONDUCTOR_SOURCE = our patched edgenode image (tx5 #194/#199 + holochain #5719)
#
# NOT cleared for fleet deploy until verified on a canary (kitsune2 wire-compat +
# go-pion feature set). Deploy to ONE non-genesis leecher's elohim-node container.

ARG STORAGE_BASE=harbor.ethosengine.com/ethosengine/elohim-storage:latest
ARG CONDUCTOR_SOURCE=harbor.ethosengine.com/ethosengine/elohim-edgenode:latest

FROM ${CONDUCTOR_SOURCE} AS conductor

FROM ${STORAGE_BASE}
# embedded-conductor mode spawns /usr/local/bin/holochain (elohim-storage/Dockerfile:229).
# The patched edgenode image carries the binary at /usr/bin/holochain (usr-merged base).
COPY --from=conductor /usr/bin/holochain /usr/local/bin/holochain
