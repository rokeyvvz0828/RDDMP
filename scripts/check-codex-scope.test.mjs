import assert from 'node:assert/strict';
import test from 'node:test';
import { isAllowedAssignmentBranch } from './check-codex-scope.mjs';

test('accepts only requirement branches and exact licon', () => {
  assert.equal(isAllowedAssignmentBranch('licon'), true);
  assert.equal(isAllowedAssignmentBranch('feat/REQ-20260917-082-licon-branch-exception'), true);
  assert.equal(isAllowedAssignmentBranch('docs/REQ-20260917-082-licon-branch-exception'), true);
});

test('rejects nonconforming branch values', () => {
  for (const branch of ['main', '', 'Licon', 'custom-branch', 'licon/extra', undefined]) {
    assert.equal(isAllowedAssignmentBranch(branch), false, branch || '<empty>');
  }
});
