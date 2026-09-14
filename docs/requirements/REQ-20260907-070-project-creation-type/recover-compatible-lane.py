"""Restore the exact approved local migration set without changing DB history."""

import json
from pathlib import Path
import re
import zlib

ROOT = Path(__file__).resolve().parents[3]
LOCAL = Path(__file__).with_name('local-migrations')
SOURCE = ROOT / 'server/src/platform/infrastructure/src/main/resources/db/migration'
LANE = ROOT / '.runtime-logs/req070-preserve-data/migrations'


def crc(content):
    value = zlib.crc32(''.join(content.decode('utf-8-sig').splitlines()).encode())
    return value if value < 2**31 else value - 2**32


def recover():
    manifest = json.loads((LOCAL / 'manifest.json').read_text(encoding='utf-8'))
    recovered = {}
    for item in manifest:
        name = item['name']
        if not re.fullmatch(r'V[0-9_]+__[A-Za-z0-9_]+\.sql', name):
            raise RuntimeError('Unexpected migration filename')
        for candidate in (LOCAL / name, SOURCE / name):
            if candidate.is_file():
                content = candidate.read_bytes()
                if crc(content) == item['checksum']:
                    recovered[name] = content
                    break
        else:
            raise RuntimeError('Verified migration source unavailable: ' + name)
    if LANE.exists() and {p.name for p in LANE.glob('*.sql')} - set(recovered):
        raise RuntimeError('Unexpected SQL in compatibility directory; refusing to delete it')
    LANE.mkdir(parents=True, exist_ok=True)
    for name, content in recovered.items():
        destination = LANE / name
        if not destination.exists():
            destination.write_bytes(content)
        elif crc(destination.read_bytes()) != crc(content):
            raise RuntimeError('Runtime migration differs from approved source: ' + name)
    return len(recovered)


if __name__ == '__main__':
    print('VERIFIED_MIGRATIONS=' + str(recover()))
