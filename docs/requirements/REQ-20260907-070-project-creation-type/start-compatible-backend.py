"""Start the local backend with the preserved migration lineage and no mock writes."""

import argparse
import datetime
import json
from importlib.util import module_from_spec, spec_from_file_location
import os
from pathlib import Path
import socket
import subprocess
import time
from urllib.parse import urlparse
from urllib.request import urlopen

from dotenv import dotenv_values

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.runtime-logs' / 'req070-preserve-data'
JAVA = Path('C:/Users/吕少伟/.jdks/jbr-17.0.12/bin/java.exe')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--trial', action='store_true')
    args = parser.parse_args()
    port = 18070 if args.trial else 8080
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', port))
    spec = spec_from_file_location('migration_lane', Path(__file__).with_name('recover-compatible-lane.py'))
    lane = module_from_spec(spec)
    spec.loader.exec_module(lane)
    lane.recover()
    settings = dotenv_values(ROOT / '.env')
    endpoint = urlparse(settings['DB_URL'].removeprefix('jdbc:'))
    if endpoint.hostname not in ('localhost', '127.0.0.1'):
        raise RuntimeError('Nonlocal development database refused')
    env = dict(os.environ)
    env.update({k: v for k, v in settings.items() if v is not None})
    env.update(MOCK_DATA_ENABLED='false', WORKFLOW_SEEDED_DEFINITION_PUBLISHER_ENABLED='false',
               SPRING_FLYWAY_LOCATIONS='filesystem:' + (OUT / 'migrations').as_posix(),
               SPRING_FLYWAY_ENABLED='true', SPRING_FLYWAY_VALIDATE_ON_MIGRATE='true')
    if args.trial:
        target = json.loads((OUT / 'trial-target.json').read_text(encoding='utf-8'))
        env.update(DB_URL='jdbc:mysql://127.0.0.1:13370/' + target['database'] +
                   '?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai',
                   DB_USERNAME='root', DB_PASSWORD=settings['MYSQL_ROOT_PASSWORD'])
    classpath = str(ROOT / 'server/src/platform/boot/target/classes') + os.pathsep + (OUT / 'boot-classpath.txt').read_text().strip()
    for filename in classpath.split(os.pathsep):
        if not Path(filename).exists():
            raise RuntimeError('Missing backend classpath entry')
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    name = 'trial-backend' if args.trial else 'backend'
    with (OUT / f'{name}-{stamp}.out.log').open('xb') as stdout, (OUT / f'{name}-{stamp}.err.log').open('xb') as stderr:
        process = subprocess.Popen([str(JAVA), '-cp', classpath, 'com.ccb.boot.CcbApplication',
                                    '--spring.profiles.active=local', '--server.address=127.0.0.1',
                                    f'--server.port={port}'], env=env, cwd=ROOT,
                                   stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                                   creationflags=subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP)
    report = {'pid': process.pid, 'port': port, 'started_at': stamp,
              'stdout': f'{name}-{stamp}.out.log', 'stderr': f'{name}-{stamp}.err.log'}
    (OUT / f'{name}-process.json').write_text(json.dumps(report), encoding='utf-8')
    print(json.dumps({'pid': process.pid, 'port': port}), flush=True)
    for unused in range(60):
        if process.poll() is not None:
            raise RuntimeError('Backend exited during startup; inspect sanitized diagnostics')
        try:
            with urlopen(f'http://127.0.0.1:{port}/actuator/health', timeout=2) as response:
                if json.load(response).get('status') == 'UP':
                    print('BACKEND_HEALTH=UP', flush=True)
                    return
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError('Backend health is not UP yet; process remains available for diagnosis')


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error))
        raise SystemExit(1)
    except Exception as error:
        print(f'Startup failed ({type(error).__name__}); sensitive details suppressed')
        raise SystemExit(1)
