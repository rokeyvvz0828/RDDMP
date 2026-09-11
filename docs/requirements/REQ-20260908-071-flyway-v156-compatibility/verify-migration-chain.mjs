import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const migrationDir = path.resolve('server/src/platform/infrastructure/src/main/resources/db/migration')
const files = fs.readdirSync(migrationDir).filter((name) => /^V[^_]+__.*\.sql$/.test(name))
const versions = new Map()
for (const file of files) {
  const version = file.slice(1, file.indexOf('__'))
  assert(!versions.has(version), `duplicate Flyway version ${version}: ${versions.get(version)} and ${file}`)
  versions.set(version, file)
}
assert.equal(versions.get('156'), 'V156__platform_operation_audit.sql')
assert.equal(versions.get('200'), 'V200__rename_release_drill_plan_menu.sql')

const menu = fs.readFileSync(path.join(migrationDir, versions.get('200')), 'utf8')
assert.match(menu, /SET menu_name = '投产方案'/)
assert.match(menu, /route_name = 'ReleaseOperationsDrillPlans'/)

const normalizer = fs.readFileSync(new URL('./NormalizeFlywayHistory.java', import.meta.url), 'utf8')
assert.match(normalizer, /LEGACY_MENU_V156 = 1191321843/)
assert.match(normalizer, /LEGACY_LOCAL_V200 = 91870892/)
assert.match(normalizer, /Partial audit schema detected; refusing normalization/)
assert.match(normalizer, /flyway_schema_history_req071_backup/)
assert.match(normalizer, /refusing overwrite/)
assert.match(normalizer, /NORMALIZATION_DRY_RUN_OK/)
console.log(`PASS: ${files.length} unique Flyway versions; canonical V156/V200 and guarded legacy normalization verified`)
