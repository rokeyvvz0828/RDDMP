import fs from 'node:fs';
import path from 'node:path';
import { isPublicPackage, packageOwner, readJsonYaml, root } from './governance-utils.mjs';

const manifest = readJsonYaml('governance/modules.yaml');
const violations = [];
const artifactOwners = new Map(
  Object.entries(manifest.modules || {})
    .filter(([, definition]) => definition.maven?.artifact)
    .map(([name, definition]) => [definition.maven.artifact, name]),
);

function collectFiles(directory, suffix) {
  const found = [];
  const absolute = path.join(root, directory);
  if (!fs.existsSync(absolute)) return found;
  for (const entry of fs.readdirSync(absolute, { withFileTypes: true })) {
    const relative = path.join(directory, entry.name);
    if (entry.isDirectory()) found.push(...collectFiles(relative, suffix));
    else if (entry.name.endsWith(suffix)) found.push(relative);
  }
  return found;
}

for (const [moduleName, definition] of Object.entries(manifest.modules || {})) {
  const maven = definition.maven;
  if (!maven) continue;

  const pom = fs.readFileSync(path.join(root, maven.pom), 'utf8');
  const dependencyArtifacts = [...pom.matchAll(/<dependency>[\s\S]*?<artifactId>([^<]+)<\/artifactId>[\s\S]*?<\/dependency>/g)]
    .map((match) => match[1].trim());
  for (const artifact of dependencyArtifacts) {
    const targetModule = artifactOwners.get(artifact);
    if (targetModule && targetModule !== moduleName && !(definition.allowed_dependencies || []).includes(targetModule)) {
      violations.push(`${maven.pom}: ${moduleName} depends on undeclared module ${targetModule} (${artifact})`);
    }
  }

  const moduleDirectory = path.dirname(maven.pom);
  for (const file of collectFiles(path.join(moduleDirectory, 'src'), '.java')) {
    const source = fs.readFileSync(path.join(root, file), 'utf8');
    const imports = [...source.matchAll(/^import\s+(?:static\s+)?(com\.ccb\.[\w.]+);/gm)].map((match) => match[1]);
    for (const importedName of imports) {
      const targetModule = packageOwner(manifest, importedName);
      if (!targetModule || targetModule === moduleName) continue;
      if (!(definition.allowed_dependencies || []).includes(targetModule)) {
        violations.push(`${file}: ${moduleName} imports undeclared module ${targetModule}: ${importedName}`);
        continue;
      }
      if (!isPublicPackage(manifest.modules[targetModule], importedName)) {
        violations.push(`${file}: ${moduleName} imports non-public package of ${targetModule}: ${importedName}`);
      }
    }
  }
}

for (const [moduleName, definition] of Object.entries(manifest.modules || {})) {
  if (!['platform', 'shared'].includes(definition.type)) continue;
  for (const dependency of definition.allowed_dependencies || []) {
    if (manifest.modules[dependency]?.type === 'business') {
      violations.push(`${moduleName}: ${definition.type} module must not depend on business module ${dependency}`);
    }
  }
}

if (violations.length) {
  console.error(`Module boundary check failed:\n${violations.join('\n')}`);
  process.exit(1);
}
console.log(`Module boundary check passed for ${artifactOwners.size} Maven module(s).`);
