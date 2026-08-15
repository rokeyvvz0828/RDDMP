import fs from 'node:fs';
import path from 'node:path';
import {readJsonYaml, root} from './governance-utils.mjs';

const controlRoot = path.join(root, '.ai-control');
const originalRoot = path.join(controlRoot, 'original');
const requirementsRoot = path.join(controlRoot, 'requirements');
const violations = [];

const expectedRootEntries = new Set(['README.md', 'original', 'requirements']);
const expectedOriginalFiles = new Set([
  'baseline.json',
  'control-plan.json',
  'execution-T1.json',
  'execution-T2.json',
  'execution-T3.json',
  'model.json',
  'observation-T1.json',
  'observation-T2.json',
  'observation-T3.json',
  'state.json',
]);
const requirementFilePattern = /^(baseline|control-plan|convergence|correction(?:-[A-Za-z0-9-]+)?|design|handoff|model|state|execution(?:-[A-Za-z0-9-]+)?|observation(?:-[A-Za-z0-9-]+)?)\.json$/;

for (const entry of fs.readdirSync(controlRoot, {withFileTypes: true})) {
  if (entry.name === '.DS_Store') continue;
  if (!expectedRootEntries.has(entry.name)) violations.push(`unexpected .ai-control root entry: ${entry.name}`);
}

for (const directory of [originalRoot, requirementsRoot]) {
  if (!fs.existsSync(directory) || !fs.statSync(directory).isDirectory()) {
    violations.push(`missing ledger directory: ${path.relative(root, directory)}`);
  }
}

if (fs.existsSync(originalRoot)) {
  const actual = new Set(fs.readdirSync(originalRoot));
  for (const file of expectedOriginalFiles) {
    if (!actual.has(file)) violations.push(`missing original ledger file: ${file}`);
  }
  for (const file of actual) {
    if (!expectedOriginalFiles.has(file)) violations.push(`unexpected original ledger file: ${file}`);
  }
}

let requirementCount = 0;
let jsonCount = 0;
if (fs.existsSync(requirementsRoot)) {
  for (const entry of fs.readdirSync(requirementsRoot, {withFileTypes: true})) {
    if (!entry.isDirectory() || !/^[a-z0-9][a-z0-9-]*$/.test(entry.name)) {
      violations.push(`invalid requirement ledger entry: ${entry.name}`);
      continue;
    }
    requirementCount += 1;
    const files = fs.readdirSync(path.join(requirementsRoot, entry.name), {withFileTypes: true});
    if (!files.length) violations.push(`empty requirement ledger directory: ${entry.name}`);
    for (const file of files) {
      if (!file.isFile() || !requirementFilePattern.test(file.name)) {
        violations.push(`invalid ledger file: requirements/${entry.name}/${file.name}`);
        continue;
      }
      jsonCount += 1;
      const fullPath = path.join(requirementsRoot, entry.name, file.name);
      try {
        const value = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
        if (entry.name.startsWith('req-') && value.topic && value.topic !== entry.name) {
          violations.push(`topic mismatch: requirements/${entry.name}/${file.name}`);
        }
      } catch (error) {
        violations.push(`invalid JSON: requirements/${entry.name}/${file.name}: ${error.message}`);
      }
    }
  }
}

if (fs.existsSync(originalRoot)) {
  for (const file of expectedOriginalFiles) {
    const fullPath = path.join(originalRoot, file);
    if (!fs.existsSync(fullPath)) continue;
    jsonCount += 1;
    try {
      JSON.parse(fs.readFileSync(fullPath, 'utf8'));
    } catch (error) {
      violations.push(`invalid JSON: original/${file}: ${error.message}`);
    }
  }
}

const requirementDocsRoot = path.join(root, 'docs/requirements');
for (const entry of fs.readdirSync(requirementDocsRoot, {withFileTypes: true})) {
  const scopePath = path.join(requirementDocsRoot, entry.name, 'codex-task-scope.yaml');
  if (!entry.isDirectory() || !fs.existsSync(scopePath)) continue;
  const scope = readJsonYaml(path.relative(root, scopePath));
  if (scope.completion?.control_mode !== 'full') continue;
  const prefix = scope.completion.control_prefix;
  const ledgerDirectory = `.ai-control/requirements/${prefix}`;
  const ledgerPattern = `${ledgerDirectory}/*.json`;
  const ledgerPath = path.join(root, ledgerDirectory);
  if (!fs.existsSync(ledgerPath)) violations.push(`${entry.name}: missing ledger directory ${ledgerDirectory}`);
  if (!scope.scope?.writable_paths?.includes(ledgerPattern)) violations.push(`${entry.name}: writable_paths must include ${ledgerPattern}`);
  const statePath = path.join(ledgerPath, 'state.json');
  let converged = false;
  if (fs.existsSync(statePath)) {
    try {
      converged = JSON.parse(fs.readFileSync(statePath, 'utf8')).phase === 'converged';
    } catch {
      // Malformed JSON is reported by the ledger parser above.
    }
  }
  for (const evidence of scope.completion.acceptance_evidence || []) {
    if (!evidence.startsWith(`${ledgerDirectory}/`)) {
      violations.push(`${entry.name}: invalid acceptance evidence ${evidence}`);
    } else if (converged && !fs.existsSync(path.join(root, evidence))) {
      violations.push(`${entry.name}: converged task is missing acceptance evidence ${evidence}`);
    }
  }
}

if (violations.length) {
  console.error(`AI control layout check failed:\n${violations.join('\n')}`);
  process.exit(1);
}

console.log(`AI control layout check passed for ${requirementCount} requirement directories and ${jsonCount} JSON files.`);
