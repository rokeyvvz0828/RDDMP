import fs from 'node:fs';
import path from 'node:path';
import { existingPrefix, readJsonYaml, root } from './governance-utils.mjs';

const manifest = readJsonYaml('governance/modules.yaml');
const violations = [];
const requiredFiles = [
  'AGENTS.md',
  'CONTRIBUTING.md',
  'docs/governance/README.md',
  'CODEX-DEVELOPMENT-GUIDE.md',
  'docs/governance/PROJECT-RULES.md',
  'docs/governance/CODEX-CODING-RULES.md',
  'docs/governance/GITHUB-RULES.md',
  'docs/architecture/MODULES.md',
  'docs/requirements/TEMPLATE.md',
  'docs/requirements/codex-task-scope.template.yaml',
  'governance/dependency-review-config.yml',
  '.agents/skills/rddmp-delivery-engineer/SKILL.md',
  'scripts/check-development-entry.mjs',
  '.github/CODEOWNERS',
  '.github/workflows/security.yml',
];

for (const file of requiredFiles) {
  if (!fs.existsSync(path.join(root, file))) violations.push(`missing required governance file: ${file}`);
}

const codeownersFile = path.join(root, '.github/CODEOWNERS');
const codeowners = fs.existsSync(codeownersFile) ? fs.readFileSync(codeownersFile, 'utf8') : '';
const activeOwners = codeowners.split('\n').filter((line) => line.trim() && !line.trim().startsWith('#'));
if (!activeOwners.length) violations.push('.github/CODEOWNERS has no active rules');

const artifacts = new Set();
for (const [name, definition] of Object.entries(manifest.modules || {})) {
  const owners = definition.owners || {};
  if (!owners.primary || owners.primary === 'UNASSIGNED') violations.push(`${name}: primary owner is missing`);
  if (!owners.backup || owners.backup === 'UNASSIGNED') violations.push(`${name}: backup owner is missing`);
  if (!Array.isArray(owners.approvers) || !owners.approvers.includes(owners.primary)) {
    violations.push(`${name}: primary owner must be an approver`);
  }
  if (!Array.isArray(definition.paths) || !definition.paths.length) violations.push(`${name}: paths are missing`);
  for (const declaredPath of [...(definition.paths || []), ...(definition.public_contracts || [])]) {
    const prefix = existingPrefix(declaredPath);
    if (prefix && !fs.existsSync(path.join(root, prefix))) violations.push(`${name}: declared path is missing: ${declaredPath}`);
  }
  const artifact = definition.maven?.artifact;
  if (artifact) {
    if (artifacts.has(artifact)) violations.push(`${name}: duplicate Maven artifact ${artifact}`);
    artifacts.add(artifact);
    if (!fs.existsSync(path.join(root, definition.maven.pom || ''))) violations.push(`${name}: Maven pom is missing`);
  }
  for (const dependency of definition.allowed_dependencies || []) {
    if (!manifest.modules?.[dependency]) violations.push(`${name}: unknown allowed dependency ${dependency}`);
  }
}

if (violations.length) {
  console.error(`Governance check failed:\n${violations.join('\n')}`);
  process.exit(1);
}
console.log(`Governance check passed for ${Object.keys(manifest.modules || {}).length} module(s).`);
