import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { root } from './governance-utils.mjs';

const migrationDirectory = 'server/src/platform/infrastructure/src/main/resources/db/migration';
const absoluteDirectory = path.join(root, migrationDirectory);
const violations = [];
const versions = new Map();
const allowedSqlCallbacks = new Set([
  'beforeMigrate__ensure_flowable_event_registry_metadata.sql',
]);
const callbacks = [];

for (const file of fs.readdirSync(absoluteDirectory).filter((name) => name.endsWith('.sql'))) {
  if (allowedSqlCallbacks.has(file)) {
    callbacks.push(file);
    continue;
  }
  const match = file.match(/^V(\d+)__([a-z0-9_]+)\.sql$/i);
  if (!match) {
    violations.push(`${file}: expected V<number>__description.sql`);
    continue;
  }
  const version = Number(match[1]);
  if (versions.has(version)) violations.push(`${file}: duplicates Flyway version V${version} used by ${versions.get(version)}`);
  versions.set(version, file);
}

const args = process.argv.slice(2);
const option = (name) => {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
};
const base = option('--base');
const head = option('--head') || 'HEAD';
if (base) {
  const changes = execFileSync('git', ['diff', '--name-status', `${base}...${head}`, '--', migrationDirectory], {
    cwd: root,
    encoding: 'utf8',
  }).trim().split('\n').filter(Boolean);
  for (const line of changes) {
    const [status, ...files] = line.split(/\s+/);
    if (status !== 'A') violations.push(`${files.join(' -> ')}: published Flyway migrations are append-only (status ${status})`);
  }
}

if (violations.length) {
  console.error(`Flyway migration check failed:\n${violations.join('\n')}`);
  process.exit(1);
}
console.log(`Flyway migration check passed for ${versions.size} migration(s) and ${callbacks.length} approved callback(s)${base ? ` against ${base}` : ''}.`);
