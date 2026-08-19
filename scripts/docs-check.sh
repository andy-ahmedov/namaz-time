#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PYDOCS'
from pathlib import Path
import json
import re

root = Path('.').resolve()
ignored_directory_names = {'.git', '.gradle', 'build', 'dist', 'node_modules'}


def repository_files(pattern: str):
    for path in root.rglob(pattern):
        if not path.is_file():
            continue
        relative = path.relative_to(root)
        if any(part in ignored_directory_names for part in relative.parts[:-1]):
            continue
        yield path


required = [
    'README.md', 'START_HERE.md', 'AGENTS.md', 'PLANS.md',
    'ARCHITECTURE.md', 'PRODUCT_REQUIREMENTS.md', 'PRAYER_TIMES_DATA.md',
    'DATA_MODEL.md', 'API_CONTRACT.md', 'UI_UX_SPEC.md',
    'TEST_STRATEGY.md', 'SECURITY_PRIVACY.md', 'OPERATIONS.md',
    'BLACK_BOX_VALIDATION_PLAN.md', 'CODEX_TASKS.md', 'SOURCES.md',
    'contracts/openapi.yaml', 'contracts/prayer-snapshot.schema.json',
    'contracts/source-record.schema.json',
    'examples/synthetic-prayer-snapshot.json',
    'research/evidence/islamapp-1.6.2-static-summary.json'
]
missing = [p for p in required if not (root / p).is_file()]
if missing:
    raise SystemExit('Missing required files:\n' + '\n'.join(missing))

for path in repository_files('*.json'):
    with path.open(encoding='utf-8') as handle:
        json.load(handle)

openapi = (root / 'contracts/openapi.yaml').read_text(encoding='utf-8')
for token in (
    'openapi: 3.1.0', 'paths:', 'components:',
    '/v1/devices/pair:', '/v1/snapshots/{snapshotId}:'
):
    if token not in openapi:
        raise SystemExit(f'contracts/openapi.yaml missing token: {token}')
try:
    import yaml  # type: ignore
except Exception:
    yaml = None
if yaml is not None:
    yaml.safe_load(openapi)

try:
    import jsonschema  # type: ignore
except Exception:
    jsonschema = None
if jsonschema is not None:
    snapshot_schema = json.loads((root / 'contracts/prayer-snapshot.schema.json').read_text(encoding='utf-8'))
    source_schema = json.loads((root / 'contracts/source-record.schema.json').read_text(encoding='utf-8'))
    jsonschema.Draft202012Validator.check_schema(snapshot_schema)
    jsonschema.Draft202012Validator.check_schema(source_schema)
    example = json.loads((root / 'examples/synthetic-prayer-snapshot.json').read_text(encoding='utf-8'))
    jsonschema.Draft202012Validator(snapshot_schema).validate(example)
else:
    example = json.loads((root / 'examples/synthetic-prayer-snapshot.json').read_text(encoding='utf-8'))

if example.get('data_classification') != 'synthetic':
    raise SystemExit('Example must be explicitly synthetic')
if 'Not real prayer times' not in example.get('source', {}).get('attribution', ''):
    raise SystemExit('Synthetic fixture must visibly warn that times are not real')

link_re = re.compile(r'(?<!!)\[[^\]]*\]\(([^)]+)\)')
errors = []
for md in repository_files('*.md'):
    text = md.read_text(encoding='utf-8')
    for raw in link_re.findall(text):
        target = raw.strip().split()[0].strip('<>')
        if target.startswith(('http://', 'https://', 'mailto:', '#')):
            continue
        target = target.split('#', 1)[0]
        if not target:
            continue
        resolved = (md.parent / target).resolve()
        try:
            resolved.relative_to(root)
        except ValueError:
            errors.append(f'{md.relative_to(root)} -> outside root: {target}')
            continue
        if not resolved.exists():
            errors.append(f'{md.relative_to(root)} -> missing: {target}')
if errors:
    raise SystemExit('Broken relative links:\n' + '\n'.join(errors))

for path in repository_files('*'):
    low = path.name.lower()
    forbidden = (
        low.endswith(('.apk', '.aab', '.apks', '.xapk'))
        or 'params.decrypted' in low
        or '4k-background-contact' in low
        or 'decompile' in low
        or 'disasm' in low
    )
    if forbidden:
        raise SystemExit(f'Prohibited artifact: {path.relative_to(root)}')
    if path.stat().st_size > 5 * 1024 * 1024:
        raise SystemExit(f'Unexpected file over 5 MiB: {path.relative_to(root)}')

summary = json.loads((root / 'research/evidence/islamapp-1.6.2-static-summary.json').read_text(encoding='utf-8'))
san = summary.get('sanitization', {})
for field in ('raw_apk_included', 'raw_or_decrypted_competitor_dataset_included', 'embedded_secret_included', 'proprietary_asset_included'):
    if san.get(field) is not False:
        raise SystemExit(f'Sanitization field must be false: {field}')

print('docs-check: PASS')
PYDOCS
