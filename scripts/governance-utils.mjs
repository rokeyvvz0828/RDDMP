import fs from 'node:fs';
import path from 'node:path';

export const root = process.cwd();

export function readJsonYaml(file) {
  try {
    return JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
  } catch (error) {
    throw new Error(`${file} must be JSON-compatible YAML: ${error.message}`);
  }
}

export function normalize(file) {
  return file.split(path.sep).join('/').replace(/^\.\//, '');
}

export function globToRegExp(pattern) {
  const escaped = pattern
    .replace(/[|\\{}()[\]^$+?.]/g, '\\$&')
    .replace(/\*\*\//g, '::GLOBSTAR_SLASH::')
    .replace(/\*\*/g, '::GLOBSTAR::')
    .replace(/\*/g, '[^/]*')
    .replace(/::GLOBSTAR_SLASH::/g, '(?:.*/)?')
    .replace(/::GLOBSTAR::/g, '.*');
  return new RegExp(`^${escaped}$`);
}

export function matches(file, patterns = []) {
  return patterns.some((pattern) => globToRegExp(pattern).test(normalize(file)));
}

export function moduleForFile(manifest, file) {
  return Object.entries(manifest.modules || {})
    .find(([, definition]) => matches(file, definition.paths || []))?.[0] || null;
}

export function packageOwner(manifest, packageName) {
  return Object.entries(manifest.modules || {}).find(([, definition]) =>
    (definition.maven?.java_packages || []).some((prefix) => packageName === prefix || packageName.startsWith(`${prefix}.`)),
  )?.[0] || null;
}

export function isPublicPackage(definition, packageName) {
  return (definition?.maven?.public_packages || [])
    .some((prefix) => packageName === prefix || packageName.startsWith(`${prefix}.`));
}

export function existingPrefix(pattern) {
  return pattern.split('*')[0].replace(/\/$/, '');
}
