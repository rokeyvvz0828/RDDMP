import { spawnSync } from 'node:child_process';

const checks = [
  'scripts/check-governance.mjs',
  'scripts/check-repo-skill.mjs',
  'scripts/check-ai-control-layout.mjs',
  'scripts/check-module-boundaries.mjs',
  'scripts/check-flyway-migrations.mjs',
];

for (const check of checks) {
  const result = spawnSync(process.execPath, [check], {stdio: 'inherit'});
  if (result.status !== 0) process.exit(result.status || 1);
}
console.log('All repository governance checks passed.');
