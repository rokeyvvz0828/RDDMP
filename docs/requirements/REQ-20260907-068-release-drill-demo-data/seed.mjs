import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'

const fixtures = JSON.parse(readFileSync(new URL('./fixtures.json', import.meta.url), 'utf8'))
const mode = process.argv[2]
assert.ok(['--dry-run', '--apply', '--verify'].includes(mode), 'Use --dry-run, --apply or --verify')
function query(sql) {
  const result = spawnSync('docker', ['exec', '-i', 'ccb-platform-mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --database=ccb_platform_licon --default-character-set=utf8mb4 --batch --raw --skip-column-names'],
    { input: sql, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 })
  if (result.status !== 0) throw new Error('Local MySQL command failed: ' + (result.stderr || result.error?.code || result.status))
  return result.stdout.trim()
}
const roundIds = fixtures.map(f => f.id)
const stepIds = fixtures.flatMap(f => f.stepIds)
function snapshot() {
  const entries = []
  for (const [table, ids] of [['rel_release_drill_round', roundIds], ['rel_release_drill_step', stepIds]]) {
    const columns = query('SHOW COLUMNS FROM ' + table).split('\n').map(line => line.split('\t')[0])
    assert.ok(columns.every(c => /^[a-z_]+$/.test(c)))
    const rows = query('SELECT JSON_ARRAY(' + columns.join(',') + ') FROM ' + table + ' WHERE tenant_id=1 AND project_id IN (940001,940002) AND id NOT IN (' + ids.join(',') + ') ORDER BY id')
    entries.push({ table, rows: rows ? rows.split('\n').length : 0, sha256: createHash('sha256').update(rows).digest('hex') })
  }
  return entries
}
function verify() {
  const output = query(`SELECT JSON_OBJECT('id',r.id,'projectId',r.project_id,'roundNo',r.round_no,'name',r.round_name,'status',r.status,'planId',r.release_plan_id,'environmentId',r.environment_id,'stepCount',(SELECT COUNT(*) FROM rel_release_drill_step s WHERE s.tenant_id=r.tenant_id AND s.project_id=r.project_id AND s.drill_round_id=r.id AND s.deleted=0)) FROM rel_release_drill_round r JOIN rel_release_plan p ON p.id=r.release_plan_id AND p.tenant_id=r.tenant_id AND p.project_id=r.project_id AND p.deleted=0 JOIN rel_release_drill_environment e ON e.id=r.environment_id AND e.tenant_id=r.tenant_id AND e.project_id=r.project_id AND e.deleted=0 WHERE r.tenant_id=1 AND r.deleted=0 AND r.id IN (${roundIds.join(',')}) ORDER BY r.id`)
  const rounds = output ? output.split('\n').map(line => JSON.parse(line)) : []
  assert.equal(rounds.length, 6)
  for (const expected of fixtures) {
    const round = rounds.find(r => r.id === expected.id)
    assert.equal(round.projectId, expected.projectId)
    assert.equal(round.status, expected.status)
    assert.equal(round.name, expected.name)
    assert.equal(round.stepCount, 6)
  }
  const steps = query(`SELECT JSON_OBJECT('id',id,'projectId',project_id,'roundId',drill_round_id,'seqNo',seq_no,'start',planned_start,'end',planned_end,'status',status,'version',row_version,'actor',created_by) FROM rel_release_drill_step WHERE tenant_id=1 AND deleted=0 AND id IN (${stepIds.join(',')}) ORDER BY drill_round_id,seq_no`).split('\n').map(line => JSON.parse(line))
  assert.equal(steps.length, 36)
  for (const round of fixtures) {
    const rows = steps.filter(s => s.roundId === round.id)
    assert.deepEqual(rows.map(s => s.id), round.stepIds)
    assert.deepEqual(rows.map(s => s.seqNo), [1,2,3,4,5,6])
    rows.forEach((step,i) => {
      assert.equal(step.projectId, round.projectId)
      assert.ok(step.end > step.start)
      if(i) assert.ok(step.start > rows[i-1].end)
      assert.ok(['PENDING','RUNNING','COMPLETED','SKIPPED'].includes(step.status))
      assert.equal(step.version, 0)
      assert.equal(step.actor, 0)
    })
  }
  const totals = query("SELECT JSON_OBJECT('projectId',project_id,'rounds',COUNT(*)) FROM rel_release_drill_round WHERE tenant_id=1 AND deleted=0 AND project_id IN (940001,940002) GROUP BY project_id").split('\n').map(line => JSON.parse(line))
  return { rounds, stepCount: steps.length, totals }
}
try {
  const before = snapshot()
  if (mode === '--verify') {
    console.log(JSON.stringify({ mode, database: 'ccb_platform_licon', verification: verify(), originalRows: before }, null, 2))
  } else {
    let sql = readFileSync(new URL('./seed.sql', import.meta.url), 'utf8')
    if (mode === '--dry-run') sql = sql.replace('\nCOMMIT;', '\nROLLBACK;')
    const raw = query(sql)
    const result = JSON.parse(raw.split('\n').at(-1))
    const after = snapshot()
    assert.deepEqual(after, before, 'Pre-existing rows changed')
    const verification = mode === '--apply' ? verify() : { rolledBack: true, batchRows: Number(query('SELECT COUNT(*) FROM rel_release_drill_round WHERE id IN (' + roundIds.join(',') + ')')) }
    console.log(JSON.stringify({ mode, database: 'ccb_platform_licon', result, before, after, verification }, null, 2))
  }
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
