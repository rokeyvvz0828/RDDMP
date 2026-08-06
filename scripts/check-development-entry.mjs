import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { root } from './governance-utils.mjs';

const args = new Set(process.argv.slice(2));
const requirePlugin = args.has('--require-plugin');
const violations = [];
const warnings = [];
const requiredFiles = [
  'AGENTS.md',
  'README.md',
  'CODEX-DEVELOPMENT-GUIDE.md',
  'docs/governance/PROJECT-RULES.md',
  'docs/governance/CODEX-CODING-RULES.md',
  'docs/integration/frontend-ui-contract.md',
  '.agents/skills/rddmp-delivery-engineer/SKILL.md',
  'web/src/modules/delivery-showcase',
];

for (const file of requiredFiles) {
  if (!fs.existsSync(path.join(root, file))) violations.push(`missing development entry: ${file}`);
}

const read = (file) => fs.existsSync(path.join(root, file)) ? fs.readFileSync(path.join(root, file), 'utf8') : '';
const rootAgent = read('AGENTS.md');
const guide = read('CODEX-DEVELOPMENT-GUIDE.md');
const frontendRules = read('web/src/AGENTS.md');
for (const [label, source, terms] of [
  ['AGENTS.md', rootAgent, ['CODEX-DEVELOPMENT-GUIDE.md', 'control-engineering', 'delivery-showcase']],
  ['CODEX-DEVELOPMENT-GUIDE.md', guide, ['AGENTS.md', 'control-engineering', 'delivery-showcase']],
  ['web/src/AGENTS.md', frontendRules, ['delivery-showcase']],
]) {
  for (const term of terms) if (!source.includes(term)) violations.push(`${label} must reference ${term}`);
}

function findCodex() {
  const candidates = [process.env.CODEX_CLI_PATH, '/Applications/ChatGPT.app/Contents/Resources/codex', 'codex'].filter(Boolean);
  for (const candidate of candidates) {
    const result = spawnSync(candidate, ['--version'], { encoding: 'utf8', stdio: 'ignore' });
    if (result.status === 0) return candidate;
  }
  return null;
}

const codex = findCodex();
if (!codex) {
  const message = 'Codex CLI not available; plugin installation could not be verified in this environment';
  if (requirePlugin) violations.push(message);
  else warnings.push(message);
} else {
  const result = spawnSync(codex, ['plugin', 'list', '--json'], { encoding: 'utf8' });
  if (result.status !== 0) {
    const message = `Codex plugin list failed: ${String(result.stderr || '').trim() || 'unknown error'}`;
    if (requirePlugin) violations.push(message);
    else warnings.push(message);
  } else {
    try {
      const records = JSON.parse(result.stdout);
      const installed = Array.isArray(records) ? records : records.installed;
      const match = Array.isArray(installed) && installed.find((item) =>
        item?.id === 'control-engineering@control-engineering-local' ||
        item?.plugin_id === 'control-engineering@control-engineering-local' ||
        item?.pluginId === 'control-engineering@control-engineering-local' ||
        item?.name === 'control-engineering' && (item?.marketplace === 'control-engineering-local' || item?.marketplaceName === 'control-engineering-local'),
      );
      if (!match || match.enabled === false || match.installed === false) {
        const message = 'control-engineering@control-engineering-local is not installed and enabled';
        if (requirePlugin) violations.push(message);
        else warnings.push(message);
      }
    } catch {
      const message = 'Codex plugin list did not return valid JSON';
      if (requirePlugin) violations.push(message);
      else warnings.push(message);
    }
  }
}

if (warnings.length) console.warn(`Development entry warnings:\n${warnings.map((item) => `- ${item}`).join('\n')}`);
if (violations.length) {
  console.error(`Development entry check failed:\n${violations.map((item) => `- ${item}`).join('\n')}`);
  process.exit(1);
}
console.log('Development entry check passed.');
