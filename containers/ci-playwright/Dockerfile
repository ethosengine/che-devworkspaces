#podman build --pull --no-cache -t harbor.ethosengine.com/ethosengine/ci-playwright:latest .
#podman push harbor.ethosengine.com/ethosengine/ci-playwright:latest

# CI Playwright Image — browser automation companion to ci-builder.
# Inherits all CI tooling from ci-builder (kubectl, pnpm, sonar-scanner,
# nerdctl/buildctl, JDK, system chromium) and adds Playwright with the
# chromium browser bundle pre-installed so pipelines skip the download
# on every run.
FROM harbor.ethosengine.com/ethosengine/ci-builder:latest

# Pin the Playwright release that ships in this image. Projects whose
# lockfile matches get an instant browser-cache hit; mismatches just
# redownload on first run.
ARG PLAYWRIGHT_VERSION=1.59.1

# Shared browser cache path — works regardless of which user the pod runs as.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN npm install -g playwright@${PLAYWRIGHT_VERSION} && \
    playwright install --with-deps chromium && \
    chmod -R a+rx /ms-playwright
