import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
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

const pluginId = 'control-engineering@control-engineering-local';

function codexHomes() {
  if (process.env.RDDMP_CODEX_HOME) return [process.env.RDDMP_CODEX_HOME];
  return [...new Set([process.env.CODEX_HOME, path.join(os.homedir(), '.codex')].filter(Boolean))];
}

function readCodexConfig(codexHome) {
  const configPath = path.join(codexHome, 'config.toml');
  return fs.existsSync(configPath) ? fs.readFileSync(configPath, 'utf8') : '';
}

function configuredCliPaths() {
  const configured = [];
  for (const codexHome of codexHomes()) {
    const config = readCodexConfig(codexHome);
    const match = config.match(/^\s*CODEX_CLI_PATH\s*=\s*['"]([^'"]+)['"]\s*$/m);
    if (match) configured.push(match[1]);
  }
  return configured;
}

function findCodex() {
  const explicitCandidates = [process.env.RDDMP_CODEX_CLI_PATH, process.env.CODEX_CLI_PATH].filter(Boolean);
  const fallbackCandidates = process.env.RDDMP_CODEX_HOME
    ? []
    : [...configuredCliPaths(), '/Applications/ChatGPT.app/Contents/Resources/codex', 'codex.exe', 'codex'];
  const candidates = [...new Set([...explicitCandidates, ...fallbackCandidates])];
  for (const candidate of candidates) {
    const result = spawnSync(candidate, ['--version'], { encoding: 'utf8', stdio: 'ignore' });
    if (result.status === 0) return candidate;
  }
  return null;
}

function installedFromCli(records) {
  const installed = Array.isArray(records) ? records : records?.installed;
  return Array.isArray(installed) && installed.some((item) =>
    item?.id === pluginId ||
    item?.plugin_id === pluginId ||
    item?.pluginId === pluginId ||
    item?.name === 'control-engineering' &&
      (item?.marketplace === 'control-engineering-local' || item?.marketplaceName === 'control-engineering-local'),
  );
}

function installedFromDesktopState() {
  for (const codexHome of codexHomes()) {
    const config = readCodexConfig(codexHome);
    const escapedPluginId = pluginId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const section = config.match(new RegExp(`\\[plugins\\."${escapedPluginId}"\\]([\\s\\S]*?)(?=\\r?\\n\\[|$)`));
    if (!section || !/^\s*enabled\s*=\s*true\s*$/m.test(section[1])) continue;

    const cacheRoot = path.join(codexHome, 'plugins', 'cache', 'control-engineering-local', 'control-engineering');
    if (!fs.existsSync(cacheRoot)) continue;
    const versions = fs.readdirSync(cacheRoot, { withFileTypes: true }).filter((entry) => entry.isDirectory());
    for (const version of versions) {
      const versionRoot = path.join(cacheRoot, version.name);
      const manifestPath = path.join(versionRoot, '.codex-plugin', 'plugin.json');
      const skillPath = path.join(versionRoot, 'skills', 'control-engineering', 'SKILL.md');
      if (!fs.existsSync(manifestPath) || !fs.existsSync(skillPath)) continue;
      try {
        const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
        if (manifest?.name === 'control-engineering' && manifest?.version === version.name) return true;
      } catch {
        // 损坏或非 JSON manifest 不能作为安装证据。
      }
    }
  }
  return false;
}

const codex = findCodex();
let pluginInstalled = false;
let cliFailure = null;
if (codex) {
  const result = spawnSync(codex, ['plugin', 'list', '--json'], { encoding: 'utf8' });
  if (result.status === 0) {
    try {
      pluginInstalled = installedFromCli(JSON.parse(result.stdout));
    } catch {
      cliFailure = 'Codex plugin list did not return valid JSON';
    }
  } else {
    cliFailure = `Codex plugin list failed: ${String(result.stderr || '').trim() || 'unknown error'}`;
  }
}

if (!pluginInstalled) pluginInstalled = installedFromDesktopState();

if (pluginInstalled) {
  // CLI 与桌面端可能使用不同的插件状态存储；任一受支持来源都必须给出完整安装证据。
} else if (!codex) {
  const message = 'Codex CLI not available; plugin installation could not be verified in this environment';
  if (requirePlugin) violations.push(message);
  else warnings.push(message);
} else {
  const message = `${pluginId} is not installed and enabled${cliFailure ? `; ${cliFailure}` : ''}`;
  if (requirePlugin) violations.push(message);
  else warnings.push(message);
}

if (warnings.length) console.warn(`Development entry warnings:\n${warnings.map((item) => `- ${item}`).join('\n')}`);
if (violations.length) {
  console.error(`Development entry check failed:\n${violations.map((item) => `- ${item}`).join('\n')}`);
  process.exit(1);
}
console.log('Development entry check passed.');
