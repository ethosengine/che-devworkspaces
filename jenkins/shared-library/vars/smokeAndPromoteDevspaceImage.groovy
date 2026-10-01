#!/usr/bin/env groovy

/**
 * Smoke-test a freshly pushed CANDIDATE image on shem, then promote it.
 *
 * Why: shem's CPUs are x86-64-v2 (no AVX2). The build hosts support v3, so a
 * build can succeed and still produce an image that dies at its first
 * instruction on shem (2026-10-01). Only running it there proves it starts.
 * See docs/2026-10-01-x86-64-v2-workspace-image-plan.md (steps 4 and 5).
 *
 * Flow: (1) a pod pinned to shem runs containers/smoke/v2-smoke.sh inside the
 * candidate image — a failed smoke fails the build and nothing is promoted;
 * (2) on success the candidate's digest gains the promote tag via the Harbor
 * REST API (a retag, no rebuild or re-push). Pair with buildDevspaceImage's
 * holdLatest so the promote tag is moved ONLY here.
 *
 * Usage:
 *   smokeAndPromoteDevspaceImage(
 *     imageName: 'udi-plus',
 *     candidateTag: env.IMAGE_TAG_GIT,     // tag the smoke runs against
 *     promoteTag: env.IMAGE_TAG_LATEST,    // tag moved after a green smoke
 *     registry: 'harbor.ethosengine.com/devspaces'
 *   )
 *
 * Needs: no agent on the calling stage (this opens its own pods); `checkout scm`
 * must work (multibranch). The pod's agent (jnlp) container also lands on shem,
 * so the cluster's inbound-agent image must itself run on x86-64-v2.
 */
def call(Map config) {
    if (!config.imageName) {
        error('imageName is required')
    }
    if (!config.candidateTag) {
        error('candidateTag is required')
    }
    if (!config.promoteTag) {
        error('promoteTag is required')
    }

    def registry = config.registry ?: 'harbor.ethosengine.com/devspaces'
    def project = registry.split('/').last()
    def image = "${registry}/${config.imageName}:${config.candidateTag}"

    // ------------------------------------------------------------------
    // Smoke on shem. The durable `sh` step writes its log and result files
    // into a directory the jnlp container (uid 1000) created with mode 0755,
    // so fsGroup alone is not enough: as the image's own user (10001) the
    // step dies with "process apparently never started". The smoke container
    // therefore runs as the agent's uid, with gid 0 so the image's
    // group-writable home still works, the same shape as Che's arbitrary UID.
    // ------------------------------------------------------------------
    echo "=== Smoke ${image} on shem ==="
    podTemplate(cloud: 'kubernetes', yaml: """
apiVersion: v1
kind: Pod
spec:
  nodeSelector:
    kubernetes.io/hostname: shem
  tolerations:
    - key: remote-wan
      operator: Equal
      value: 'true'
      effect: NoSchedule
  securityContext:
    fsGroup: 1000
  containers:
    - name: smoke
      image: ${image}
      imagePullPolicy: Always
      command:
        - cat
      tty: true
      securityContext:
        runAsUser: 1000
        runAsGroup: 0
""") {
        node(POD_LABEL) {
            checkout scm
            container('smoke') {
                sh 'bash containers/smoke/v2-smoke.sh'
            }
        }
    }
    echo "✅ ${image} passed the smoke on shem"

    // ------------------------------------------------------------------
    // Promote: add the promote tag to the candidate's artifact.
    // ------------------------------------------------------------------
    echo "=== Promoting ${config.imageName}:${config.candidateTag} -> ${config.promoteTag} ==="
    podTemplate(cloud: 'kubernetes', yaml: '''
apiVersion: v1
kind: Pod
spec:
  nodeSelector:
    node-type: edge
  containers:
    - name: builder
      image: harbor.ethosengine.com/ethosengine/ci-builder:latest
      command:
        - cat
      tty: true
''') {
        node(POD_LABEL) {
            container('builder') {
                withCredentials([usernamePassword(
                    credentialsId: 'harbor-robot-registry',
                    passwordVariable: 'HARBOR_PASSWORD',
                    usernameVariable: 'HARBOR_USERNAME'
                )]) {
                    sh """#!/bin/bash
                        set -euo pipefail
                        AUTH_B64=\$(printf '%s:%s' "\$HARBOR_USERNAME" "\$HARBOR_PASSWORD" | base64 -w0)
                        STATUS=\$(curl -sS -o /dev/null -w '%{http_code}' -X POST \\
                            -H "Authorization: Basic \$AUTH_B64" \\
                            -H 'Content-Type: application/json' \\
                            -d '{"name":"${config.promoteTag}"}' \\
                            "https://harbor.ethosengine.com/api/v2.0/projects/${project}/repositories/${config.imageName}/artifacts/${config.candidateTag}/tags")
                        if [ "\$STATUS" != "201" ]; then
                            echo "❌ Harbor tag create returned HTTP \$STATUS"
                            exit 1
                        fi
                        echo "✅ ${config.imageName}:${config.promoteTag} now points at ${config.candidateTag}"
                    """
                }
            }
        }
    }
}
