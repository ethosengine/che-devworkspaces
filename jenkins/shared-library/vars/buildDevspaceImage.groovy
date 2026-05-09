#!/usr/bin/env groovy

/**
 * Shared library for building DevSpaces images.
 *
 * Builds with BuildKit (OCI worker in the buildkitd sidecar) and pushes
 * directly from BuildKit to Harbor. No tarball roundtrip through nerdctl
 * + containerd — that path was hitting the builder container's
 * ephemeral-storage limit on multi-GB images (ci-builder-nix, ~3GB
 * after the Nix store, failed at "no image was built" 2026-05-09 even
 * though BuildKit's build/export both succeeded).
 *
 * Auth: buildctl reads ~/.docker/config.json on the *client* (builder
 * container) and forwards credentials to buildkitd over the gRPC
 * session — so creds never need to be mounted into the sidecar.
 *
 * Usage:
 *   buildDevspaceImage(
 *     imageName: 'udi-plus',
 *     dockerfile: 'containers/udi-plus',
 *     registry: 'harbor.ethosengine.com/devspaces',
 *     baseImage: 'quay.io/devfile/universal-developer-image:ubi9-latest',
 *     buildArgs: [:],
 *     skipBaseImageCheck: false
 *   )
 */
def call(Map config) {
    if (!config.imageName) {
        error("imageName is required")
    }
    if (!config.dockerfile) {
        error("dockerfile is required")
    }

    def registry = config.registry ?: 'harbor.ethosengine.com/devspaces'
    def baseImage = config.baseImage
    def buildArgs = config.buildArgs ?: [:]
    def skipBaseImageCheck = config.skipBaseImageCheck ?: false
    def skipPush = config.skipPush ?: false
    def skipSecurityScan = config.skipSecurityScan ?: false
    def skipSmokeTests = config.skipSmokeTests ?: false
    def forceBuild = config.forceBuild ?: false

    def datestamp = sh(script: 'date +%Y-%m-%d', returnStdout: true).trim()
    def gitHash = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()

    def imageTagLatest = 'latest'
    def imageTagDated = datestamp
    def imageTagGit = gitHash

    def imageTags = [
        latest: imageTagLatest,
        dated: imageTagDated,
        git: imageTagGit
    ]

    env.IMAGE_TAG_LATEST = imageTagLatest
    env.IMAGE_TAG_DATED = imageTagDated
    env.IMAGE_TAG_GIT = imageTagGit
    env.GIT_HASH = gitHash

    // Project (last path segment of the registry) — used by all Harbor REST calls below.
    def project = registry.split('/').last()

    // ------------------------------------------------------------------
    // Base-image-update check
    // ------------------------------------------------------------------
    def skipBuild = false
    if (baseImage && !forceBuild && !skipBaseImageCheck) {
        echo "Checking for updates to base image: ${baseImage}"

        def currentImageId = sh(
            script: "nerdctl -n k8s.io images -q ${baseImage} 2>/dev/null || echo 'none'",
            returnStdout: true
        ).trim()

        echo "Current image ID: ${currentImageId}"
        sh "nerdctl -n k8s.io pull ${baseImage}"

        def newImageId = sh(
            script: "nerdctl -n k8s.io images -q ${baseImage}",
            returnStdout: true
        ).trim()

        echo "New image ID: ${newImageId}"

        if (currentImageId == newImageId && currentImageId != 'none') {
            echo "⚠️  No updates to base image detected"
            echo "Set FORCE_BUILD=true to rebuild anyway"
            skipBuild = true
        } else {
            echo "✅ Base image has updates, proceeding with build"
        }
    }

    if (skipBuild) {
        echo "⏭️  Skipping build - no base image updates"
        return [
            skipped: true,
            tags: imageTags
        ]
    }

    // ------------------------------------------------------------------
    // Build context preparation
    // ------------------------------------------------------------------
    sh """#!/bin/bash
        set -euo pipefail
        rm -rf /tmp/build-context/${config.imageName}
        mkdir -p /tmp/build-context/${config.imageName}
        cp -r ${config.dockerfile}/* /tmp/build-context/${config.imageName}/
    """

    // Translate buildArgs map → repeated --opt build-arg:K=V flags.
    def buildArgFlags = buildArgs.collect { k, v -> "--opt build-arg:${k}=${v}" }.join(' ')

    // ------------------------------------------------------------------
    // Build (and push, unless skipPush)
    // ------------------------------------------------------------------
    if (skipPush) {
        echo "=== Building ${config.imageName} (skipPush=true; validation only, no artifact) ==="
        sh """#!/bin/bash
            set -euo pipefail
            cd /tmp/build-context/${config.imageName}

            buildctl --addr unix:///run/buildkit/buildkitd.sock build \\
                --frontend dockerfile.v0 \\
                --local context=. \\
                --local dockerfile=. \\
                --no-cache \\
                ${buildArgFlags}
        """
        echo "✅ ${config.imageName} build validated"
    } else {
        // Push directly from BuildKit → Harbor. Multi-name output writes
        // all three tags (latest, dated, git-hash) to the same digest
        // in one push.
        def outputNames = [
            "${registry}/${config.imageName}:${imageTagLatest}",
            "${registry}/${config.imageName}:${imageTagDated}",
            "${registry}/${config.imageName}:${imageTagGit}"
        ].join(',')
        def outputArg = "type=image,\"name=${outputNames}\",push=true"

        echo "=== Building & pushing ${config.imageName} to Harbor ==="
        withCredentials([usernamePassword(
            credentialsId: 'harbor-robot-registry',
            passwordVariable: 'HARBOR_PASSWORD',
            usernameVariable: 'HARBOR_USERNAME'
        )]) {
            sh """#!/bin/bash
                set -euo pipefail
                cd /tmp/build-context/${config.imageName}

                # buildctl forwards docker-config auth to buildkitd over the
                # gRPC session — creds live only in the builder container.
                DOCKER_CONFIG=\$(mktemp -d)
                export DOCKER_CONFIG
                trap 'rm -rf "\$DOCKER_CONFIG"' EXIT
                REGISTRY_HOST=\$(echo "${registry}" | cut -d/ -f1)
                AUTH_B64=\$(printf '%s:%s' "\$HARBOR_USERNAME" "\$HARBOR_PASSWORD" | base64 -w0)
                cat > "\$DOCKER_CONFIG/config.json" <<EOF
{"auths":{"\$REGISTRY_HOST":{"auth":"\$AUTH_B64"}}}
EOF

                buildctl --addr unix:///run/buildkit/buildkitd.sock build \\
                    --frontend dockerfile.v0 \\
                    --local context=. \\
                    --local dockerfile=. \\
                    --no-cache \\
                    ${buildArgFlags} \\
                    --output '${outputArg}'
            """
        }
        echo "✅ ${config.imageName} built and pushed to Harbor"
    }

    // ------------------------------------------------------------------
    // Smoke test: verify the manifest is fetchable from Harbor
    // (replaces the old `nerdctl images | grep` no-op — meaningful now
    // that the image is never written to local containerd)
    // ------------------------------------------------------------------
    if (!skipPush && !skipSmokeTests) {
        echo "=== Verifying ${config.imageName}:${imageTagDated} manifest in Harbor ==="
        withCredentials([usernamePassword(
            credentialsId: 'harbor-robot-registry',
            passwordVariable: 'HARBOR_PASSWORD',
            usernameVariable: 'HARBOR_USERNAME'
        )]) {
            sh """#!/bin/bash
                set -euo pipefail
                AUTH_B64=\$(printf '%s:%s' "\$HARBOR_USERNAME" "\$HARBOR_PASSWORD" | base64 -w0)
                STATUS=\$(curl -sS -o /dev/null -w '%{http_code}' \\
                    -H "Authorization: Basic \$AUTH_B64" \\
                    -H 'Accept: application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.v2+json,application/vnd.oci.image.index.v1+json,application/vnd.docker.distribution.manifest.list.v2+json' \\
                    "https://harbor.ethosengine.com/v2/${project}/${config.imageName}/manifests/${imageTagDated}")
                if [ "\$STATUS" != "200" ]; then
                    echo "❌ Harbor manifest fetch returned HTTP \$STATUS"
                    exit 1
                fi
                echo "✅ Manifest in Harbor for ${config.imageName}:${imageTagDated} (HTTP 200)"
            """
        }
    }

    // ------------------------------------------------------------------
    // Trigger Harbor security scan
    // ------------------------------------------------------------------
    if (!skipSecurityScan && !skipPush) {
        echo "=== Triggering Harbor security scan for ${config.imageName} ==="

        withCredentials([usernamePassword(
            credentialsId: 'harbor-robot-registry',
            passwordVariable: 'HARBOR_PASSWORD',
            usernameVariable: 'HARBOR_USERNAME'
        )]) {
            sh """
                AUTH_HEADER="Authorization: Basic \$(echo -n "\$HARBOR_USERNAME:\$HARBOR_PASSWORD" | base64)"

                wget --post-data="" \\
                  --header="accept: application/json" \\
                  --header="Content-Type: application/json" \\
                  --header="\$AUTH_HEADER" \\
                  -S -O- \\
                  "https://harbor.ethosengine.com/api/v2.0/projects/${project}/repositories/${config.imageName}/artifacts/${imageTagDated}/scan" || \\
                echo "Scan request for ${config.imageName} submitted"
            """
        }

        echo "✅ Security scan initiated for ${config.imageName}"
    }

    // ------------------------------------------------------------------
    // Clean up old images from Harbor (keep latest + 3 most recent dated)
    // ------------------------------------------------------------------
    if (!skipPush) {
        echo "=== Cleaning up old ${config.imageName} images from Harbor ==="

        def keepCount = 3

        withCredentials([usernamePassword(
            credentialsId: 'harbor-robot-registry',
            passwordVariable: 'HARBOR_PASSWORD',
            usernameVariable: 'HARBOR_USERNAME'
        )]) {
            try {
                def artifactsJson = sh(
                    script: """
                        AUTH_HEADER="Authorization: Basic \$(echo -n "\$HARBOR_USERNAME:\$HARBOR_PASSWORD" | base64)"

                        wget -q \\
                          --header="accept: application/json" \\
                          --header="\$AUTH_HEADER" \\
                          -O- \\
                          "https://harbor.ethosengine.com/api/v2.0/projects/${project}/repositories/${config.imageName}/artifacts?page_size=100&with_tag=true"
                    """,
                    returnStdout: true
                ).trim()

                def artifacts = readJSON text: artifactsJson

                // Collect dated tags (YYYY-MM-DD format) with their push times.
                // readJSON uses net.sf.json which represents JSON null as a
                // singleton JSONNull object, *not* Groovy null — so plain
                // truthy checks like `if (!artifact.tags)` don't catch it,
                // and `entry.name` blows up with "No such property: name for
                // class: net.sf.json.JSONNull" when an entry is JSONNull.
                def datedArtifacts = []
                for (artifact in artifacts) {
                    if (!artifact?.tags || artifact.tags instanceof net.sf.json.JSONNull) continue
                    def tags = []
                    for (entry in artifact.tags) {
                        if (entry == null || entry instanceof net.sf.json.JSONNull) continue
                        def n = entry.name
                        if (n != null && !(n instanceof net.sf.json.JSONNull)) {
                            tags.add(n.toString())
                        }
                    }
                    def datedTag = tags.find { it ==~ /\d{4}-\d{2}-\d{2}/ }
                    if (datedTag) {
                        datedArtifacts.add([
                            digest: artifact.digest,
                            datedTag: datedTag,
                            tags: tags,
                            pushTime: artifact.push_time
                        ])
                    }
                }

                // Sort by dated tag descending (newest first). toSorted
                // returns a new list and avoids the CPS sandbox warning
                // that .sort { closure } emits in Jenkins shared libraries.
                datedArtifacts = datedArtifacts.toSorted { a, b -> b.datedTag <=> a.datedTag }

                if (datedArtifacts.size() > keepCount) {
                    def toDelete = datedArtifacts.drop(keepCount)
                    echo "Found ${datedArtifacts.size()} dated versions, keeping ${keepCount}, deleting ${toDelete.size()}"

                    for (old in toDelete) {
                        // Skip if this artifact also carries the 'latest' tag
                        if (old.tags.contains('latest')) {
                            echo "Skipping ${old.datedTag} (also tagged as latest)"
                            continue
                        }
                        echo "Deleting old artifact: ${old.tags.join(', ')} (digest: ${old.digest})"
                        sh """
                            AUTH_HEADER="Authorization: Basic \$(echo -n "\$HARBOR_USERNAME:\$HARBOR_PASSWORD" | base64)"

                            wget --method=DELETE -q \\
                              --header="accept: application/json" \\
                              --header="\$AUTH_HEADER" \\
                              -O- \\
                              "https://harbor.ethosengine.com/api/v2.0/projects/${project}/repositories/${config.imageName}/artifacts/\$(echo '${old.digest}' | sed 's/:/%3A/g')" || \\
                            echo "Warning: Failed to delete ${old.datedTag}"
                        """
                    }

                    echo "✅ Old ${config.imageName} images cleaned up from Harbor"
                } else {
                    echo "Only ${datedArtifacts.size()} dated versions found, nothing to clean up"
                }
            } catch (err) {
                echo "⚠️  Harbor cleanup failed (non-fatal): ${err.message}"
            }
        }
    }

    // ------------------------------------------------------------------
    // Return build info
    // ------------------------------------------------------------------
    return [
        skipped: false,
        tags: imageTags,
        registry: registry,
        fullImageName: "${registry}/${config.imageName}:${imageTagDated}"
    ]
}
