"""Run real Flyway against an isolated trial or the authorized local database."""

import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from urllib.parse import urlparse

from dotenv import dotenv_values
import pymysql

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.runtime-logs' / 'req070-preserve-data'
JDK = Path('C:/Users/吕少伟/.jdks/jbr-17.0.12/bin')
CONTAINER = 'req070-compatible-mysql'


def execute(args, **kwargs):
    result = subprocess.run(args, stderr=subprocess.PIPE, **kwargs)
    if result.returncode:
        raise RuntimeError(f'Command failed: {args[0]}, exit {result.returncode}')
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['create-trial', 'restore-trial', 'trial-validate', 'trial-migrate', 'current-validate', 'current-migrate'])
    args = parser.parse_args()
    settings = dotenv_values(ROOT / '.env')
    endpoint = urlparse(settings['DB_URL'].removeprefix('jdbc:'))
    if endpoint.hostname not in ('localhost', '127.0.0.1') or (endpoint.port or 3306) != 3306:
        raise RuntimeError('Current target must be the verified local development database')
    env = dict(os.environ)
    env.update({k: v for k, v in settings.items() if v is not None})
    if args.action in ('create-trial', 'restore-trial'):
        if args.action == 'create-trial':
            execute(['docker', 'run', '-d', '--name', CONTAINER, '-p', '127.0.0.1:13370:3306',
                     '-e', 'MYSQL_ROOT_PASSWORD', '-e', 'MYSQL_DATABASE=ccb_trial', 'mysql:8.4',
                     '--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci'],
                    env=env, stdout=subprocess.PIPE)
        for unused in range(50):
            probe = subprocess.run(['docker', 'exec', CONTAINER, 'sh', '-c',
                                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot ccb_trial -e "SELECT 1"'],
                                   capture_output=True)
            if probe.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('Trial MySQL did not become ready')
        backup = json.loads((OUT / 'latest-backup.json').read_text(encoding='utf-8'))
        filename = backup['backup_file']
        if not re.fullmatch(r'before-[0-9]{8}-[0-9]{6}\.sql', filename):
            raise RuntimeError('Invalid backup filename')
        with (OUT / filename).open('rb') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != backup['sha256']:
                raise RuntimeError('Backup checksum mismatch')
        trial_database = 'req070_trial_' + datetime.datetime.now().strftime('%Y%m%d_%H%M%S_%f')
        execute(['docker', 'exec', CONTAINER, 'sh', '-c',
                 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "$1"', 'create',
                 f'CREATE DATABASE `{trial_database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'],
                stdout=subprocess.PIPE)
        with (OUT / filename).open('rb') as stream:
            execute(['docker', 'exec', '-i', CONTAINER, 'sh', '-c',
                     'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"', 'restore', trial_database],
                    stdin=stream, stdout=subprocess.PIPE)
        (OUT / 'trial-target.json').write_text(json.dumps({'database': trial_database,
            'backup_file': filename, 'backup_sha256': backup['sha256']}), encoding='utf-8')
        print('TRIAL_RESTORE_SUCCESS=true')
        return
    if args.action.startswith('trial-'):
        target = json.loads((OUT / 'trial-target.json').read_text(encoding='utf-8'))
        if not re.fullmatch(r'req070_trial_[0-9_]+', target['database']):
            raise RuntimeError('Invalid isolated database identifier')
        env.update(DB_URL='jdbc:mysql://127.0.0.1:13370/' + target['database'] + '?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai',
                   DB_USERNAME='root', DB_PASSWORD=settings['MYSQL_ROOT_PASSWORD'])
    libs = ROOT / '.runtime-logs/flyway-repair-runtime/BOOT-INF/lib'
    jars = [p for p in libs.glob('*.jar') if p.name.startswith(('flyway-', 'mysql-connector-', 'jackson-', 'gson-', 'slf4j-api-'))]
    classes = OUT / 'classes'
    classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join([str(classes)] + [str(p) for p in jars])
    execute([str(JDK / 'javac.exe'), '-cp', classpath, '-d', str(classes),
             str(Path(__file__).with_name('LocalMigration.java'))], stdout=subprocess.PIPE)
    if args.action == 'current-migrate':
        from importlib.util import module_from_spec, spec_from_file_location
        spec = spec_from_file_location('preservation', Path(__file__).with_name('verify-compatible-trial.py'))
        preservation = module_from_spec(spec)
        spec.loader.exec_module(preservation)
        report = json.loads((OUT / 'compatible-trial-verified.json').read_text(encoding='utf-8'))
        if not report.get('verified'):
            raise RuntimeError('Current migration requires a verified trial report')
        actual = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (OUT / 'migrations').glob('*.sql')}
        if actual != report['migration_hashes']:
            raise RuntimeError('Migration content changed after trial')
        with pymysql.connect(host=endpoint.hostname, port=endpoint.port or 3306,
                             user=settings['DB_USERNAME'], password=settings['DB_PASSWORD'],
                             database=endpoint.path.lstrip('/')) as current:
            for table in report['original_tables']:
                now = preservation.table_hash(current, table['table'], table['columns'])
                if now != {'count': table['count'], 'sha256': table['sha256']}:
                    raise RuntimeError('Source data changed since trial: ' + table['table'])
            for table, expected in report['protected_schemas'].items():
                if preservation.schema_hash(current, table) != expected:
                    raise RuntimeError('Protected source schema changed since trial: ' + table)
    result = subprocess.run([str(JDK / 'java.exe'), '-cp', classpath, 'LocalMigration',
                             'migrate' if args.action.endswith('-migrate') else 'validate',
                             str(OUT / 'migrations')], env=env, capture_output=True, text=True)
    # Only emit explicit helper status lines, never database URLs or driver diagnostics.
    for line in result.stdout.splitlines():
        if re.match(r'^(VALIDATION_SUCCESS|INVALID_VERSION|MIGRATIONS_EXECUTED|MIGRATION_FAILED)=', line):
            print(line)
    if result.returncode:
        raise RuntimeError(f'Flyway failed with exit {result.returncode}')


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error))
        raise SystemExit(1)
    except Exception as error:
        print(f'Operation failed ({type(error).__name__}); sensitive details suppressed')
        raise SystemExit(1)
