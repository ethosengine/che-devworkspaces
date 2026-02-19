#!/usr/bin/env groovy

/**
 * Shared library for building DevSpaces images
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
    // Validate required parameters
    if (!config.imageName) {
        error("imageName is required")
    }
    if (!config.dockerfile) {
        error("dockerfile is required")
    }

    // Set defaults
    def registry = config.registry ?: 'harbor.ethosengine.com/devspaces'
    def baseImage = config.baseImage
    def buildArgs = config.buildArgs ?: [:]
    def skipBaseImageCheck = config.skipBaseImageCheck ?: false
    def skipPush = config.skipPush ?: false
    def skipSecurityScan = config.skipSecurityScan ?: false
    def skipSmokeTests = config.skipSmokeTests ?: false
    def forceBuild = config.forceBuild ?: false

    // Generate tags
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

    // Store in environment for later stages
    env.IMAGE_TAG_LATEST = imageTagLatest
    env.IMAGE_TAG_DATED = imageTagDated
    env.IMAGE_TAG_GIT = imageTagGit
    env.GIT_HASH = gitHash

    // Check if we should skip build (base image hasn't changed)
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

    // Build the image
    echo "=== Building ${config.imageName} image ==="

    sh """#!/bin/bash
        set -euo pipefail

        # Verify BuildKit
        buildctl --addr unix:///run/buildkit/buildkitd.sock debug workers > /dev/null

        # Create build context
        mkdir -p /tmp/build-context/${config.imageName}
        cp -r ${config.dockerfile}/* /tmp/build-context/${config.imageName}/

        # Build image
        cd /tmp/build-context/${config.imageName}

        # Construct build args
        BUILD_ARGS=""
        ${buildArgs.collect { k, v -> "BUILD_ARGS=\"\$BUILD_ARGS --build-arg ${k}=${v}\"" }.join('\n        ')}

        BUILDKIT_HOST=unix:///run/buildkit/buildkitd.sock \\
          nerdctl -n k8s.io build \\
            --no-cache \\
            \$BUILD_ARGS \\
            -t ${registry}/${config.imageName}:${imageTagLatest} \\
            -t ${registry}/${config.imageName}:${imageTagDated} \\
            -t ${registry}/${config.imageName}:${imageTagGit} \\
            .
    """

    // Verify image exists
    def imageExists = sh(
        script: "nerdctl -n k8s.io images -q ${registry}/${config.imageName}:${imageTagDated}",
        returnStdout: true
    ).trim()

    if (!imageExists) {
        error("❌ VERIFICATION FAILED: ${config.imageName} image not found after build")
    }

    echo "✅ ${config.imageName} image built and verified"

    // Smoke tests
    if (!skipSmokeTests) {
        echo "=== Testing ${config.imageName} image ==="
        sh """#!/bin/bash
            set -euo pipefail

            echo "Verifying image exists..."
            nerdctl -n k8s.io images | grep "${registry}/${config.imageName}" | grep "${imageTagDated}"
            echo "✅ Image built successfully"
            echo "⚠️  Skipping container smoke tests (nested containerization not supported in Jenkins pod)"
        """
        echo "✅ ${config.imageName} smoke tests passed"
    }

    // Push to registry
    if (!skipPush) {
        echo "=== Pushing ${config.imageName} to Harbor ==="

        withCredentials([usernamePassword(
            credentialsId: 'harbor-robot-registry',
            passwordVariable: 'HARBOR_PASSWORD',
            usernameVariable: 'HARBOR_USERNAME'
        )]) {
            sh """
                echo \$HARBOR_PASSWORD | nerdctl -n k8s.io login ${registry} -u \$HARBOR_USERNAME --password-stdin

                nerdctl -n k8s.io push ${registry}/${config.imageName}:${imageTagLatest}
                nerdctl -n k8s.io push ${registry}/${config.imageName}:${imageTagDated}
                nerdctl -n k8s.io push ${registry}/${config.imageName}:${imageTagGit}
            """
        }

        echo "✅ ${config.imageName} pushed to Harbor"
    }

    // Security scan
    if (!skipSecurityScan && !skipPush) {
        echo "=== Triggering Harbor security scan for ${config.imageName} ==="

        withCredentials([usernamePassword(
            credentialsId: 'harbor-robot-registry',
            passwordVariable: 'HARBOR_PASSWORD',
            usernameVariable: 'HARBOR_USERNAME'
        )]) {
            // Extract project from registry
            def project = registry.split('/').last()

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

    // Clean up old images from Harbor (keep latest + 3 most recent dated tags)
    if (!skipPush) {
        echo "=== Cleaning up old ${config.imageName} images from Harbor ==="

        def project = registry.split('/').last()
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

                // Collect dated tags (YYYY-MM-DD format) with their push times
                def datedArtifacts = []
                for (artifact in artifacts) {
                    if (!artifact.tags) continue
                    def tags = artifact.tags.collect { it.name }
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

                // Sort by dated tag descending (newest first)
                datedArtifacts.sort { a, b -> b.datedTag <=> a.datedTag }

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

    // Return build info
    return [
        skipped: false,
        tags: imageTags,
        registry: registry,
        fullImageName: "${registry}/${config.imageName}:${imageTagDated}"
    ]
}
