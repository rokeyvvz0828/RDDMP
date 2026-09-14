"""Check original-column row hashes and protected schemas without exposing data."""

import datetime
from decimal import Decimal
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse

from dotenv import dotenv_values
import pymysql

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.runtime-logs' / 'req070-preserve-data'


def encoded(value):
    if isinstance(value, bytes):
        return {'bytes': value.hex()}
    if isinstance(value, (datetime.datetime, datetime.date, datetime.time)):
        return {'datetime': value.isoformat()}
    if isinstance(value, Decimal):
        return {'decimal': str(value)}
    raise TypeError('Unsupported row value')


def quote(name):
    if not name.replace('_', '').isalnum():
        raise ValueError('Unexpected identifier')
    return '`' + name + '`'


def table_hash(connection, table, columns):
    with connection.cursor() as cursor:
        cursor.execute('SELECT ' + ','.join(quote(c) for c in columns) + ' FROM ' + quote(table))
        hashes = sorted(hashlib.sha256(json.dumps(row, default=encoded, ensure_ascii=True,
                        separators=(',', ':')).encode()).hexdigest() for row in cursor.fetchall())
    return {'count': len(hashes), 'sha256': hashlib.sha256(''.join(hashes).encode()).hexdigest()}


def schema_hash(connection, table):
    queries = [
        'SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,COLLATION_NAME,CHARACTER_SET_NAME,'
        'GENERATION_EXPRESSION,COLUMN_COMMENT FROM information_schema.columns '
        'WHERE table_schema=DATABASE() AND table_name=%s ORDER BY ORDINAL_POSITION',
        'SELECT INDEX_NAME,NON_UNIQUE,INDEX_TYPE,COLUMN_NAME,SEQ_IN_INDEX,SUB_PART '
        'FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=%s '
        'ORDER BY INDEX_NAME,SEQ_IN_INDEX',
        'SELECT CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME '
        'FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=%s '
        'ORDER BY CONSTRAINT_NAME,ORDINAL_POSITION',
        'SELECT ENGINE,TABLE_COLLATION,ROW_FORMAT FROM information_schema.tables '
        'WHERE table_schema=DATABASE() AND table_name=%s'
    ]
    values = []
    with connection.cursor() as cursor:
        for sql in queries:
            cursor.execute(sql, (table,))
            values.append(cursor.fetchall())
    return hashlib.sha256(json.dumps(values, default=encoded, ensure_ascii=True).encode()).hexdigest()


def main():
    settings = dotenv_values(ROOT / '.env')
    endpoint = urlparse(settings['DB_URL'].removeprefix('jdbc:'))
    if endpoint.hostname not in ('localhost', '127.0.0.1'):
        raise RuntimeError('Nonlocal source refused')
    original = pymysql.connect(host=endpoint.hostname, port=endpoint.port or 3306,
                               user=settings['DB_USERNAME'], password=settings['DB_PASSWORD'],
                               database=endpoint.path.lstrip('/'))
    target = json.loads((OUT / 'trial-target.json').read_text(encoding='utf-8'))
    trial = pymysql.connect(host='127.0.0.1', port=13370, user='root',
                            password=settings['MYSQL_ROOT_PASSWORD'], database=target['database'])
    try:
        with original.cursor() as cursor:
            cursor.execute('SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.columns '
                           'WHERE table_schema=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION')
            columns = {}
            for table, column in cursor.fetchall():
                columns.setdefault(table, []).append(column)
        evidence = []
        for table, names in columns.items():
            if table == 'flyway_schema_history':
                continue
            before = table_hash(original, table, names)
            after = table_hash(trial, table, names)
            if before != after:
                raise RuntimeError('Original data differs for table: ' + table)
            evidence.append({'table': table, 'columns': names, **before})
        schemas = {}
        for table in columns:
            if not table.startswith('rel_'):
                continue
            before = schema_hash(original, table)
            after = schema_hash(trial, table)
            if before != after:
                raise RuntimeError('Protected schema changed: ' + table)
            schemas[table] = before
        with trial.cursor() as cursor:
            cursor.execute('SELECT id,creation_type,updated_at FROM pm_project ORDER BY id LIMIT 1')
            row = cursor.fetchone()
            if not row:
                raise RuntimeError('No trial project available for persistence check')
            project_id, old_type, old_updated_at = row
            try:
                cursor.execute('UPDATE pm_project SET creation_type=%s WHERE id=%s', ('CONTINUATION', project_id))
                trial.commit()
                with pymysql.connect(host='127.0.0.1', port=13370, user='root',
                                     password=settings['MYSQL_ROOT_PASSWORD'], database=target['database']) as fresh:
                    with fresh.cursor() as reader:
                        reader.execute('SELECT creation_type FROM pm_project WHERE id=%s', (project_id,))
                        if reader.fetchone()[0] != 'CONTINUATION':
                            raise RuntimeError('Fresh connection did not read persisted type')
            finally:
                cursor.execute('UPDATE pm_project SET creation_type=%s,updated_at=%s WHERE id=%s',
                               (old_type, old_updated_at, project_id))
                trial.commit()
        if table_hash(original, 'pm_project', columns['pm_project']) != table_hash(trial, 'pm_project', columns['pm_project']):
            raise RuntimeError('Persistence probe did not restore original project values')
        migration_hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in (OUT / 'migrations').glob('*.sql')}
        report = {'verified': True, 'original_tables': evidence, 'protected_schemas': schemas,
                  'original_history': {'columns': columns['flyway_schema_history'],
                    **table_hash(original, 'flyway_schema_history', columns['flyway_schema_history'])},
                  'backup': target,
                  'migration_hashes': migration_hashes, 'creation_type_commit_read_passed': True,
                  'source_database_modified': False,
                  'limitations': ['same-context verification', 'no authenticated browser save replay']}
        (OUT / 'compatible-trial-verified.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        print(json.dumps({'verified': True, 'original_data_tables_unchanged': len(evidence),
                          'protected_schemas_unchanged': len(schemas), 'creation_type_commit_read_passed': True}))
    finally:
        original.close()
        trial.close()


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error))
        raise SystemExit(1)
    except Exception as error:
        print(f'Verification failed ({type(error).__name__}); sensitive details suppressed')
        raise SystemExit(1)
