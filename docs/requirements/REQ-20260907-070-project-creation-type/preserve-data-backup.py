"""Back up the local development database without displaying its contents."""

import datetime
import hashlib
import json
from pathlib import Path
import subprocess
from urllib.parse import urlparse

from dotenv import dotenv_values
import pymysql


ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.runtime-logs' / 'req070-preserve-data'


def run(args, **kwargs):
    result = subprocess.run(args, stderr=subprocess.PIPE, **kwargs)
    if result.returncode:
        raise RuntimeError(f'Command failed: {args[0]} (exit {result.returncode}); output suppressed')
    return result


def main():
    settings = dotenv_values(ROOT / '.env')
    endpoint = urlparse(settings['DB_URL'].removeprefix('jdbc:'))
    if endpoint.hostname not in ('localhost', '127.0.0.1') or (endpoint.port or 3306) != 3306:
        raise RuntimeError('Only the verified local development database is allowed')
    database = endpoint.path.lstrip('/')
    if not database or not all(c.isalnum() or c == '_' for c in database):
        raise RuntimeError('Invalid database identifier')
    if run(['git', 'check-ignore', str(OUT / 'probe.sql')], stdout=subprocess.PIPE).returncode:
        raise RuntimeError('Backup output must be Git ignored')
    OUT.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    backup = OUT / f'before-{stamp}.sql'
    # Root credentials stay inside the already configured MySQL container.
    with backup.open('xb') as stream:
        run(['docker', 'exec', 'ccb-platform-mysql', 'sh', '-c',
             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction '
             '--routines --events --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF "$1"',
             'backup', database], stdout=stream)
    with backup.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    connection = pymysql.connect(host=endpoint.hostname, port=endpoint.port or 3306,
                                 user=settings['DB_USERNAME'], password=settings['DB_PASSWORD'],
                                 database=database)
    try:
        with connection.cursor() as cursor:
            cursor.execute('SELECT table_name FROM information_schema.tables '
                           'WHERE table_schema=DATABASE() AND table_type=\'BASE TABLE\' ORDER BY table_name')
            tables = [row[0] for row in cursor.fetchall()]
            counts = {}
            for table in tables:
                if '`' in table:
                    raise RuntimeError('Unexpected identifier')
                cursor.execute(f'SELECT COUNT(*) FROM `{table}`')
                counts[table] = cursor.fetchone()[0]
    finally:
        connection.close()
    report = {'backup_file': backup.name, 'sha256': digest,
              'bytes': backup.stat().st_size, 'table_counts': counts,
              'protected_tables': [t for t in tables if t.startswith('rel_')],
              'source_database_modified': False, 'restore_verified': False}
    with (OUT / f'backup-{stamp}.json').open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2)
    (OUT / 'latest-backup.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({'backup_file': str(backup), 'bytes': report['bytes'],
                      'table_count': len(tables), 'protected_table_count': len(report['protected_tables']),
                      'source_database_modified': False}))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'Backup failed ({type(error).__name__}); sensitive details suppressed')
        raise SystemExit(1)
