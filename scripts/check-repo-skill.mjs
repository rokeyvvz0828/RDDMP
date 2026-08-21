import fs from 'node:fs';
import path from 'node:path';
import { root } from './governance-utils.mjs';

const skillDirectory = path.join(root, '.agents/skills/rddmp-delivery-engineer');
const skillFile = path.join(skillDirectory, 'SKILL.md');
const agentFile = path.join(skillDirectory, 'agents/openai.yaml');
const violations = [];

if (!fs.existsSync(skillFile)) violations.push('missing repository skill SKILL.md');
if (!fs.existsSync(agentFile)) violations.push('missing repository skill agents/openai.yaml');

if (fs.existsSync(skillFile)) {
  const source = fs.readFileSync(skillFile, 'utf8').replace(/\r\n?/g, '\n');
  const frontmatter = source.match(/^---\n([\s\S]*?)\n---/);
  if (!frontmatter) violations.push('SKILL.md must start with YAML frontmatter');
  if (!/^name:\s*rddmp-delivery-engineer\s*$/m.test(frontmatter?.[1] || '')) violations.push('SKILL.md name is invalid');
  if (!/^description:\s*\S.+$/m.test(frontmatter?.[1] || '')) violations.push('SKILL.md description is missing');
  const references = [...source.matchAll(/\]\((references\/[^)]+)\)/g)].map((match) => match[1]);
  for (const reference of references) {
    if (!fs.existsSync(path.join(skillDirectory, reference))) violations.push(`missing skill reference: ${reference}`);
  }
  if (/\b(TODO|TBD|FIXME)\b/.test(source)) violations.push('SKILL.md contains unfinished placeholders');
}

if (fs.existsSync(agentFile)) {
  const source = fs.readFileSync(agentFile, 'utf8');
  for (const field of ['display_name', 'short_description', 'default_prompt']) {
    if (!new RegExp(`^\\s*${field}:`, 'm').test(source)) violations.push(`agents/openai.yaml is missing ${field}`);
  }
  if (!source.includes('$rddmp-delivery-engineer')) violations.push('default_prompt must reference $rddmp-delivery-engineer');
}

if (violations.length) {
  console.error(`Repository skill check failed:\n${violations.join('\n')}`);
  process.exit(1);
}
console.log('Repository skill check passed.');
