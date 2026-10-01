import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const names = ['udi-plus', 'rust-nix-dev', 'udi-plus-angular', 'udi-plus-mem',
  'udi-plus-mem-rust-nix', 'base-developer-v2', 'ci-builder', 'ci-builder-nix', 'ci-playwright'];

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
        // A cascade may pass parameters (the VARIANT=v2 chain); the target stays a concrete main job.
        assert.match(call[1], /^'\/[^']+\/main', wait: true(, parameters: \[.+\])?$/);
      }
    }
  });
}

test('Harbor comparator is outside CPS; latest protection remains', () => {
  const source = readFileSync(new URL('../shared-library/vars/buildDevspaceImage.groovy', import.meta.url), 'utf8');
  assert.match(source, /@NonCPS\s+List newestDatedArtifactsFirst\(List artifacts\) \{\s*artifacts.toSorted/);
  assert.match(source, /datedArtifacts = newestDatedArtifactsFirst\(datedArtifacts\)/);
  assert.match(source, /old.tags.contains\(imageTagLatest\)/);
  assert.match(source, /def imageTagLatest = "\$\{tagPrefix\}latest"/);
});

for (const name of ['udi-plus', 'udi-plus-mem', 'udi-plus-mem-rust-nix']) {
  test(`${name}: VARIANT=v2 is isolated from latest and gated by the shem smoke`, () => {
    const source = readFileSync(new URL(`../Jenkinsfile-${name}`, import.meta.url), 'utf8');
    assert.match(source, /choice\(name: 'VARIANT', choices: \['default', 'v2'\]/);
    assert.match(source, /tagPrefix: params.VARIANT == 'v2' \? 'v2-' : ''/);
    assert.match(source, /holdLatest: params.VARIANT == 'v2'/);
    const smoke = source.slice(source.indexOf("stage('Smoke on shem')"));
    assert.match(smoke, /params.VARIANT == 'v2' && env.BUILD_RESULT == 'SUCCESS' && !params.SKIP_PUSH/);
    assert.match(smoke, /smokeAndPromoteDevspaceImage\(/);
    assert.doesNotMatch(source.slice(source.indexOf("stage('Smoke on shem')"), source.indexOf("stage('Downstream')") < 0 ? undefined : source.indexOf("stage('Downstream')")), /agent\s*\{/);
  });
}

test('shem smoke pins to shem, tolerates remote-wan, and promotes only after it', () => {
  const source = readFileSync(new URL('../shared-library/vars/smokeAndPromoteDevspaceImage.groovy', import.meta.url), 'utf8');
  assert.match(source, /kubernetes.io\/hostname: shem/);
  assert.match(source, /key: remote-wan\s+operator: Equal\s+value: 'true'\s+effect: NoSchedule/);
  assert.ok(source.indexOf('v2-smoke.sh') < source.indexOf('/tags'), 'smoke must run before the retag');
});
