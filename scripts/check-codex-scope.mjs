import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { matches, moduleForFile, normalize, readJsonYaml, root } from './governance-utils.mjs';

const args = process.argv.slice(2);
const option = (name) => {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
};
const base = option('--base') || 'HEAD~1';
const head = option('--head') || 'HEAD';
const includeWorkingTree = args.includes('--working-tree');

function changedFiles() {
  const committed = execFileSync('git', ['diff', '--name-only', `${base}...${head}`], {cwd: root, encoding: 'utf8'})
    .split('\n').filter(Boolean).map(normalize);
  const working = includeWorkingTree ? [
    ...execFileSync('git', ['diff', '--name-only', 'HEAD'], {cwd: root, encoding: 'utf8'}).split('\n'),
    ...execFileSync('git', ['ls-files', '--others', '--exclude-standard'], {cwd: root, encoding: 'utf8'}).split('\n'),
  ].filter(Boolean).map(normalize) : [];
  return [...new Set([...committed, ...working])];
}

const changed = changedFiles();
let scopeFile = option('--scope');
if (args.includes('--discover')) {
  const candidates = changed.filter((file) => /^docs\/requirements\/REQ-[^/]+\/codex-task-scope\.yaml$/.test(file));
  if (candidates.length !== 1) throw new Error(`Expected exactly one changed codex-task-scope.yaml, found ${candidates.length}`);
  scopeFile = candidates[0];
}
if (!scopeFile) throw new Error('Use --scope <file> or --discover with --base and --head');

const scope = readJsonYaml(scopeFile);
for (const key of ['requirement', 'assignment', 'codex', 'scope', 'database', 'external_access', 'required_tests', 'risk', 'completion']) {
  if (!(key in scope)) throw new Error(`${scopeFile} is missing ${key}`);
}
for (const key of ['writable_paths', 'read_only_paths', 'forbidden_paths']) {
  if (!Array.isArray(scope.scope[key])) throw new Error(`${scopeFile}.scope.${key} must be an array`);
}
if (scope.requirement.status !== 'ready') throw new Error('Codex task scope must reference a ready requirement');
if (scope.codex.production_access) throw new Error('Codex task scope must not permit production access');
if (scope.codex.contains_confidential_information && scope.requirement.codex_allowed !== false) {
  throw new Error('Confidential task must disable Codex');
}

const manifest = readJsonYaml('governance/modules.yaml');
const targetDefinition = manifest.modules?.[scope.assignment.module];
if (!targetDefinition) throw new Error(`Unknown assignment module: ${scope.assignment.module}`);
if (!scope.assignment.developer || scope.assignment.developer === 'UNASSIGNED') throw new Error('Developer must be assigned');
if (scope.assignment.module_owner !== targetDefinition.owners?.primary) throw new Error('module_owner must match modules.yaml');
if (!/^(feat|fix|hotfix|docs|chore)\/REQ-\d{8}-\d{3}-[a-z0-9-]+$/.test(scope.assignment.branch || '')) {
  throw new Error('Branch must follow <type>/REQ-YYYYMMDD-NNN-short-name');
}

const requirementFile = path.join(root, scope.requirement.document || '');
if (!fs.existsSync(requirementFile)) throw new Error('Requirement document is missing');
const requirementStatus = fs.readFileSync(requirementFile, 'utf8').match(/^status:\s*["']?([^"'\n]+)["']?\s*$/m)?.[1];
if (requirementStatus !== 'ready') throw new Error('Requirement document must have status: ready');
for (const testName of ['api', 'permission']) {
  if (!scope.required_tests.some((test) => test.name === testName)) throw new Error(`required_tests must include ${testName}`);
}

const completion = scope.completion || {};
if (!['full', 'minimal'].includes(completion.control_mode)) {
  throw new Error('completion.control_mode must be full or minimal');
}
if (completion.control_mode === 'full') {
  if (!/^req-\d{8}-\d{3}-[a-z0-9-]+$/.test(completion.control_prefix || '')) {
    throw new Error('completion.control_prefix must follow req-YYYYMMDD-NNN-short-name');
  }
  const ledgerDirectory = `.ai-control/requirements/${completion.control_prefix}`;
  const ledgerPattern = `${ledgerDirectory}/*.json`;
  if (!scope.scope.writable_paths.includes(ledgerPattern)) {
    throw new Error(`writable_paths must include the current ledger prefix: ${ledgerPattern}`);
  }
  if (!Array.isArray(completion.acceptance_evidence) || !completion.acceptance_evidence.length
      || completion.acceptance_evidence.some((file) => !file.startsWith(`${ledgerDirectory}/`))) {
    throw new Error('acceptance_evidence must list files under the current control_prefix');
  }
} else {
  if (!completion.control_justification) {
    throw new Error('minimal control mode requires completion.control_justification');
  }
  const metadataFiles = new Set([normalize(scopeFile), normalize(scope.requirement.document || '')]);
  const nonDocumentationChanges = changed.filter((file) =>
    !metadataFiles.has(file) && !file.endsWith('.md') && !file.startsWith('.ai-control/'));
  if (nonDocumentationChanges.length) {
    throw new Error(`minimal control mode only permits requirement metadata, Markdown and ledger evidence; use full mode for: ${nonDocumentationChanges.join(', ')}`);
  }
}

const violations = [];
for (const file of changed) {
  if (matches(file, scope.scope.forbidden_paths)) violations.push(`${file}: forbidden path`);
  else if (matches(file, scope.scope.read_only_paths)) violations.push(`${file}: read-only path`);
  else if (!matches(file, scope.scope.writable_paths)) violations.push(`${file}: outside writable_paths`);
}

const publicChange = changed.some((file) => {
  const moduleName = moduleForFile(manifest, file);
  const definition = moduleName ? manifest.modules[moduleName] : null;
  return definition && (
    (moduleName !== 'governance' && ['platform', 'shared', 'composition'].includes(definition.type))
    || matches(file, definition.public_contracts || [])
  );
});
if (publicChange) {
  const change = scope.public_capability_change || {};
  if (!change.required || !change.issue || !change.owner_approved || !change.old_behavior_preserved || !(change.regression_tests || []).length) {
    violations.push('public capability change requires issue, owner approval, compatibility and regression tests');
  }
}

if (violations.length) {
  console.error(`Codex scope check failed for ${scope.requirement.id}:\n${violations.join('\n')}`);
  process.exit(1);
}
console.log(`Codex scope check passed for ${scope.requirement.id}; ${changed.length} changed file(s).`);
