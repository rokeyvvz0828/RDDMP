"""Read-only post-migration checks against the pre-migration original-column hashes."""

import hashlib
from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
from urllib.parse import urlparse

from dotenv import dotenv_values
import pymysql

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.runtime-logs' / 'req070-preserve-data'


def main():
    spec = spec_from_file_location('preservation', Path(__file__).with_name('verify-compatible-trial.py'))
    preservation = module_from_spec(spec)
    spec.loader.exec_module(preservation)
    report = json.loads((OUT / 'compatible-trial-verified.json').read_text(encoding='utf-8'))
    settings = dotenv_values(ROOT / '.env')
    endpoint = urlparse(settings['DB_URL'].removeprefix('jdbc:'))
    if endpoint.hostname not in ('localhost', '127.0.0.1'):
        raise RuntimeError('Nonlocal source refused')
    with pymysql.connect(host=endpoint.hostname, port=endpoint.port or 3306,
                         user=settings['DB_USERNAME'], password=settings['DB_PASSWORD'],
                         database=endpoint.path.lstrip('/')) as current:
        for table in report['original_tables']:
            actual = preservation.table_hash(current, table['table'], table['columns'])
            if actual != {'count': table['count'], 'sha256': table['sha256']}:
                raise RuntimeError('Original data differs: ' + table['table'])
        for table, expected in report['protected_schemas'].items():
            if preservation.schema_hash(current, table) != expected:
                raise RuntimeError('Protected schema differs: ' + table)
        with current.cursor() as cursor:
            history = report['original_history']
            cursor.execute('SELECT ' + ','.join(preservation.quote(c) for c in history['columns']) +
                           " FROM flyway_schema_history WHERE version NOT IN ('199','200') OR version IS NULL")
            hashes = sorted(hashlib.sha256(json.dumps(row, default=preservation.encoded, ensure_ascii=True,
                            separators=(',', ':')).encode()).hexdigest() for row in cursor.fetchall())
            if len(hashes) != history['count'] or hashlib.sha256(''.join(hashes).encode()).hexdigest() != history['sha256']:
                raise RuntimeError('Original migration history differs')
            cursor.execute("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('199','200') AND success=1")
            if cursor.fetchone()[0] != 2:
                raise RuntimeError('Expected two successful compatibility migrations')
            cursor.execute("SELECT COUNT(*) FROM pm_project WHERE creation_type IS NULL OR creation_type NOT IN ('NEW','CONTINUATION')")
            if cursor.fetchone()[0]:
                raise RuntimeError('Invalid creation_type values')
    result = {'original_data_tables_unchanged': len(report['original_tables']),
              'protected_schemas_unchanged': len(report['protected_schemas']),
              'original_flyway_history_unchanged': True, 'new_migrations': 2,
              'creation_type_values_valid': True, 'verified': True}
    (OUT / 'current-preservation-verified.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error))
        raise SystemExit(1)
    except Exception as error:
        print(f'Verification failed ({type(error).__name__}); sensitive details suppressed')
        raise SystemExit(1)
