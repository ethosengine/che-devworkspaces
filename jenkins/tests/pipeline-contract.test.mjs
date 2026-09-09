import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const names = ['udi-plus', 'rust-nix-dev', 'udi-plus-angular', 'udi-plus-mem',
  'udi-plus-mem-rust-nix', 'ci-builder', 'ci-builder-nix', 'ci-playwright'];

for (const name of names) {
  test(`${name}: image agent ends before synchronous cascade`, () => {
    const source = readFileSync(new URL(`../Jenkinsfile-${name}`, import.meta.url), 'utf8');
    assert.match(source, /agent none/);
    assert.match(source, /disableConcurrentBuilds\(\)/);
    const imageStart = source.indexOf("stage('Image')");
    const downstreamStart = source.indexOf("stage('Downstream')");
    const image = source.slice(imageStart, downstreamStart < 0 ? undefined : downstreamStart);
    assert.match(image, /agent \{\s*kubernetes/);
    assert.match(image, /post \{/);
    assert.doesNotMatch(image, /^\s*build job:/m);
    if (downstreamStart >= 0) {
      const downstream = source.slice(downstreamStart);
      assert.doesNotMatch(downstream, /agent\s*\{|container\(|catch\s*\(/);
      assert.match(downstream, /env.BUILD_RESULT == 'SUCCESS' && !params.SKIP_PUSH/);
      for (const call of downstream.matchAll(/build job: ([^\n]+)/g)) {
        assert.match(call[1], /^'\/[^']+\/main', wait: true$/);
      }
    }
  });
}

test('Harbor comparator is outside CPS; latest protection remains', () => {
  const source = readFileSync(new URL('../shared-library/vars/buildDevspaceImage.groovy', import.meta.url), 'utf8');
  assert.match(source, /@NonCPS\s+List newestDatedArtifactsFirst\(List artifacts\) \{\s*artifacts.toSorted/);
  assert.match(source, /datedArtifacts = newestDatedArtifactsFirst\(datedArtifacts\)/);
  assert.match(source, /old.tags.contains\('latest'\)/);
});
