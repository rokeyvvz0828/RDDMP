"""Report metadata-only differences from the isolated migration trial."""

import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.runtime-logs' / 'req070-preserve-data'
CONTAINER = 'req070-preserve-data-check'


def query(database, sql):
    result = subprocess.run(['docker', 'exec', CONTAINER, 'mysql', '-uroot',
                             '-N', '-B', database, '-e', sql], capture_output=True, check=True)
    return [line.split('\t') for line in result.stdout.decode('utf-8').splitlines()]


def main():
    report = json.loads((OUT / 'backup-20260907-175858.json').read_text(encoding='utf-8'))
    before = report['table_counts']
    existing = {row[0] for row in query('ccb_trial_unicode',
                'SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()')}
    tables = [table for table in before if table in existing]
    for table in tables:
        if not table.replace('_', '').isalnum():
            raise ValueError('Unexpected identifier')
    counts_sql = ' UNION ALL '.join(f"SELECT '{table}',COUNT(*) FROM `{table}`" for table in tables)
    after = {row[0]: int(row[1]) for row in query('ccb_trial_unicode', counts_sql)}
    protected_checks = []
    for table in report['protected_tables']:
        original_ddl = query('ccb_restore', f'SHOW CREATE TABLE `{table}`')[0][1]
        migrated_ddl = query('ccb_trial_unicode', f'SHOW CREATE TABLE `{table}`')[0][1]
        original_checksum = query('ccb_restore', f'CHECKSUM TABLE `{table}`')[0][1]
        migrated_checksum = query('ccb_trial_unicode', f'CHECKSUM TABLE `{table}`')[0][1]
        protected_checks.append({'table': table,
                                 'schema_equal': original_ddl == migrated_ddl,
                                 'checksum_equal': original_checksum == migrated_checksum,
                                 'row_count_equal': before[table] == after[table]})
    result = {
        'removed_tables': [{'table': t, 'original_rows': before[t]} for t in before if t not in existing],
        'changed_row_counts': [{'table': t, 'before': before[t], 'after': after[t]}
                               for t in tables if before[t] != after[t]],
        'protected_checks': protected_checks,
        'current_database_modified': False,
        'verification_limit': 'Counts do not prove unchanged values in unprotected tables; CHECKSUM is not cryptographic.'
    }
    (OUT / 'trial-result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
