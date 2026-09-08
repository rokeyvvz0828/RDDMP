"""Generate a local-only additive migration lane; never modify the source DB."""

import hashlib
import json
from pathlib import Path
import re
import subprocess
from urllib.parse import urlparse
import xml.etree.ElementTree as ET
import zlib

from dotenv import dotenv_values
import pymysql

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.runtime-logs' / 'req070-preserve-data'
LANE = OUT / 'migrations'
SOURCE = ROOT / 'server/src/platform/infrastructure/src/main/resources/db/migration'
CONTAINER = 'req070-preserve-data-check'


def ident(value):
    if not re.fullmatch(r'[A-Za-z0-9_]+', value):
        raise ValueError('Unexpected identifier')
    return '`' + value + '`'


def checksum(content):
    value = zlib.crc32(''.join(content.decode('utf-8-sig').splitlines()).encode('utf-8'))
    return value if value < 2**31 else value - 2**32


def target_query(sql):
    result = subprocess.run(['docker', 'exec', CONTAINER, 'mysql', '-uroot', '--xml',
                             'ccb_trial_unicode', '-e', sql], capture_output=True, check=True)
    return [{f.attrib['name']: f.text for f in row}
            for row in ET.fromstring(result.stdout).findall('row')]


def column_sql(column):
    text = ident(column['COLUMN_NAME']) + ' ' + column['COLUMN_TYPE']
    if column['CHARACTER_SET_NAME']:
        text += ' CHARACTER SET ' + column['CHARACTER_SET_NAME'] + ' COLLATE ' + column['COLLATION_NAME']
    extra = column['EXTRA'] or ''
    if column['GENERATION_EXPRESSION']:
        return text + ' GENERATED ALWAYS AS (' + column['GENERATION_EXPRESSION'] + ') ' + ('STORED' if 'STORED' in extra else 'VIRTUAL')
    text += ' NULL' if column['IS_NULLABLE'] == 'YES' else ' NOT NULL'
    default = column['COLUMN_DEFAULT']
    if default is not None:
        text += ' DEFAULT ' + (default if 'DEFAULT_GENERATED' in extra else pymysql.converters.escape_str(str(default)))
    if 'auto_increment' in extra:
        text += ' AUTO_INCREMENT'
    match = re.search(r'on update (.+)', extra, re.I)
    if match:
        text += ' ON UPDATE ' + match.group(1)
    return text


def recover_script(name, expected):
    path = SOURCE / name
    if path.exists():
        content = path.read_bytes()
        if checksum(content) == expected:
            return name, content
    version = name.split('__')[0]
    for candidate in SOURCE.glob(version + '__*.sql'):
        content = candidate.read_bytes()
        if checksum(content) == expected:
            return candidate.name, content
    relative = path.relative_to(ROOT).as_posix()
    commits = subprocess.run(['git', 'log', '--all', '--format=%H', '--', relative],
                             cwd=ROOT, capture_output=True, check=True, text=True).stdout.splitlines()
    for commit in commits:
        result = subprocess.run(['git', 'show', commit + ':' + relative], cwd=ROOT, capture_output=True)
        if result.returncode == 0 and checksum(result.stdout) == expected:
            return name, result.stdout
    raise RuntimeError('Cannot recover exact historical migration: ' + name)


def main():
    settings = dotenv_values(ROOT / '.env')
    endpoint = urlparse(settings['DB_URL'].removeprefix('jdbc:'))
    if endpoint.hostname not in ('localhost', '127.0.0.1'):
        raise RuntimeError('Only local development is allowed')
    connection = pymysql.connect(host=endpoint.hostname, port=endpoint.port or 3306,
                                 user=settings['DB_USERNAME'], password=settings['DB_PASSWORD'],
                                 database=endpoint.path.lstrip('/'), cursorclass=pymysql.cursors.DictCursor)
    LANE.mkdir(parents=True, exist_ok=True)
    with connection, connection.cursor() as cursor:
        cursor.execute("SELECT COUNT(*) n FROM flyway_schema_history WHERE version='200' AND success=1")
        if cursor.fetchone()['n']:
            raise RuntimeError('Compatibility migration already applied; use recover-compatible-lane.py instead of regenerating SQL')
        cursor.execute("SELECT h.script,h.checksum FROM flyway_schema_history h WHERE h.type='SQL' AND h.success=1 "
                       "AND NOT EXISTS (SELECT 1 FROM flyway_schema_history d WHERE d.version=h.version AND d.type='DELETE')")
        history = cursor.fetchall()
        for migration in history:
            name, content = recover_script(migration['script'], migration['checksum'])
            (LANE / name).write_bytes(content)
        (LANE / 'V199__project_creation_type.sql').write_bytes((SOURCE / 'V199__project_creation_type.sql').read_bytes())

        query = ('SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,COLUMN_DEFAULT,IS_NULLABLE,EXTRA,'
                 'GENERATION_EXPRESSION,CHARACTER_SET_NAME,COLLATION_NAME,ORDINAL_POSITION '
                 'FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION')
        cursor.execute(query)
        original = {}
        for column in cursor.fetchall():
            original.setdefault(column['TABLE_NAME'], {})[column['COLUMN_NAME']] = column
        target = {}
        for column in target_query(query):
            target.setdefault(column['TABLE_NAME'], {})[column['COLUMN_NAME']] = column
        statements = []
        changes = []
        for table, desired in target.items():
            if not (table.startswith('dm_') or table == 'sys_operation_log') or table not in original:
                continue
            cursor.execute('SELECT COUNT(*) n FROM ' + ident(table))
            count = cursor.fetchone()['n']
            current = original[table]
            for name, column in desired.items():
                if name not in current:
                    if table == 'dm_dashboard_snapshot' and name == 'system_code' and count:
                        cursor.execute('SELECT COUNT(*) n FROM dm_dashboard_snapshot WHERE component_id IS NOT NULL')
                        if cursor.fetchone()['n']:
                            raise RuntimeError('Snapshot component mapping requires an explicit backfill')
                        column = dict(column, COLUMN_DEFAULT='')
                    if count and column['IS_NULLABLE'] == 'NO' and column['COLUMN_DEFAULT'] is None and not column['GENERATION_EXPRESSION']:
                        raise RuntimeError('Unsafe required-column addition: ' + table + '.' + name)
                    statements.append('ALTER TABLE ' + ident(table) + ' ADD COLUMN ' + column_sql(column) + ';')
                    changes.append('add ' + table + '.' + name)
                elif current[name]['COLUMN_TYPE'] != column['COLUMN_TYPE']:
                    if count:
                        raise RuntimeError('Nonempty type conversion requires explicit data mapping: ' + table + '.' + name)
                    statements.append('ALTER TABLE ' + ident(table) + ' MODIFY COLUMN ' + column_sql(column) + ';')
                    changes.append('empty-table type alignment ' + table + '.' + name)
            for name, column in current.items():
                if name in desired or column['IS_NULLABLE'] == 'YES' or column['COLUMN_DEFAULT'] is not None or column['GENERATION_EXPRESSION']:
                    continue
                if count:
                    raise RuntimeError('Required retained column needs a compatibility strategy: ' + table + '.' + name)
                preserved = dict(column)
                if name == 'id' and column['COLUMN_TYPE'] == 'bigint':
                    preserved['EXTRA'] = 'auto_increment'
                else:
                    preserved['IS_NULLABLE'] = 'YES'
                statements.append('ALTER TABLE ' + ident(table) + ' MODIFY COLUMN ' + column_sql(preserved) + ';')
                changes.append('retain optional legacy column ' + table + '.' + name)

        index_query = ('SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,INDEX_TYPE,COLUMN_NAME,SEQ_IN_INDEX,SUB_PART '
                       'FROM information_schema.statistics WHERE table_schema=DATABASE() '
                       'ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX')
        cursor.execute(index_query)
        current_indexes = {}
        wanted_indexes = {}
        for rows, grouped in ((cursor.fetchall(), current_indexes), (target_query(index_query), wanted_indexes)):
            for row in rows:
                grouped.setdefault((row['TABLE_NAME'], row['INDEX_NAME']), []).append(row)

        def signature(rows):
            return (str(rows[0]['NON_UNIQUE']), rows[0]['INDEX_TYPE'],
                    tuple((row['COLUMN_NAME'], str(row['SUB_PART'])) for row in rows))

        for (table, name), rows in wanted_indexes.items():
            if table not in original or not (table.startswith('dm_') or table == 'sys_operation_log'):
                continue
            if any(t == table and signature(other) == signature(rows)
                   for (t, unused), other in current_indexes.items()):
                continue
            if rows[0]['INDEX_TYPE'] != 'BTREE' or any(row['COLUMN_NAME'] is None for row in rows):
                raise RuntimeError('Index requires manual review: ' + table + '.' + name)
            index_name = 'req070_primary' if name == 'PRIMARY' else name
            if (table, index_name) in current_indexes:
                index_name = index_name[:55] + '_req070'
            columns = ','.join(ident(row['COLUMN_NAME']) +
                               ('(' + str(row['SUB_PART']) + ')' if row['SUB_PART'] else '') for row in rows)
            unique = 'UNIQUE ' if str(rows[0]['NON_UNIQUE']) == '0' else ''
            statements.append('ALTER TABLE ' + ident(table) + ' ADD ' + unique + 'INDEX ' +
                              ident(index_name) + ' (' + columns + ');')
            changes.append('add index ' + table + '.' + index_name)

        new_tables = {name for name in target if name not in original and name.startswith('dm_')}
        dependencies = {name: set() for name in new_tables}
        for row in target_query('SELECT TABLE_NAME,REFERENCED_TABLE_NAME FROM information_schema.key_column_usage '
                                'WHERE table_schema=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL'):
            if row['TABLE_NAME'] in new_tables and row['REFERENCED_TABLE_NAME'] in new_tables:
                dependencies[row['TABLE_NAME']].add(row['REFERENCED_TABLE_NAME'])
        while dependencies:
            ready = sorted(name for name, parents in dependencies.items() if not parents)
            if not ready:
                raise RuntimeError('Cyclic table dependencies require reviewed DDL')
            for table in ready:
                ddl = target_query('SHOW CREATE TABLE ' + ident(table))[0]['Create Table']
                statements.append(ddl + ';')
                changes.append('create ' + table)
                del dependencies[table]
                for parents in dependencies.values():
                    parents.discard(table)
        text = '-- Local compatibility lane: preserve legacy data, tables and permissions.\n' + '\n\n'.join(statements) + '\n'
        (LANE / 'V200__local_preserved_schema_compatibility.sql').write_text(text, encoding='utf-8')
        manifest = {'historical_scripts': len(history), 'changes': changes, 'statement_count': len(statements),
                    'sha256': hashlib.sha256(text.encode()).hexdigest(), 'source_database_modified': False,
                    'note': 'No canonical migration is marked executed by this generator. Index alignment remains subject to trial.'}
        (OUT / 'compatibility-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
        print(json.dumps(manifest))


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error))
        raise SystemExit(1)
    except Exception as error:
        print(f'Preparation failed ({type(error).__name__}); sensitive details suppressed')
        raise SystemExit(1)
