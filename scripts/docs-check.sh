#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for raw_reference in \
  design.png \
  current_design.png \
  main_with_qr.png \
  new_main_page_with_setting_icon.png \
  qr_page.png; do
  if git ls-files --error-unmatch -- "$raw_reference" >/dev/null 2>&1; then
    echo "Raw visual reference must remain outside Git: $raw_reference" >&2
    exit 1
  fi
done

python3 - <<'PYDOCS'
from pathlib import Path
import hashlib
import json
import re

root = Path('.').resolve()
ignored_directory_names = {'.git', '.gradle', 'build', 'dist', 'node_modules'}
large_file_sha256_allowlist = {
    Path('fixtures/pilot/ulyanovsk-2026/calendar_for_the_year_Ulyanovsk.pdf'):
        '82045aa209e61bef7a394bcb883bfe367e760cf16aebfb8f602b56b1cc92bd21',
}


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
        document = json.load(handle)
    pending = [document]
    while pending:
        value = pending.pop()
        if isinstance(value, dict):
            forbidden_secret_fields = {'private_key', 'private_key_ed25519_base64', 'secret_key', 'signing_seed'}
            found = forbidden_secret_fields.intersection(key.lower() for key in value)
            if found:
                raise SystemExit(f'Prohibited private signing field in {path.relative_to(root)}: {sorted(found)}')
            pending.extend(value.values())
        elif isinstance(value, list):
            pending.extend(value)

private_key_markers = (
    b'-----BEGIN ' + b'PRIVATE KEY-----',
    b'-----BEGIN ' + b'OPENSSH PRIVATE KEY-----',
    b'-----BEGIN ' + b'RSA PRIVATE KEY-----',
    b'-----BEGIN ' + b'EC PRIVATE KEY-----',
)
for path in repository_files('*'):
    if path.stat().st_size <= 1024 * 1024:
        data = path.read_bytes()
        if any(marker in data for marker in private_key_markers):
            raise SystemExit(f'Prohibited private-key material: {path.relative_to(root)}')

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
    publication_schemas = [
        json.loads(path.read_text(encoding='utf-8'))
        for path in sorted((root / 'contracts').glob('publication-*.schema.json'))
    ]
    approval_trust_schema = json.loads((root / 'contracts/approval-trust-bundle.schema.json').read_text(encoding='utf-8'))
    approval_receipt_schema = json.loads((root / 'contracts/approval-receipt.schema.json').read_text(encoding='utf-8'))
    mosque_policy_schema = json.loads((root / 'contracts/mosque-prayer-policy.schema.json').read_text(encoding='utf-8'))
    for schema in [snapshot_schema, source_schema, *publication_schemas, approval_trust_schema, approval_receipt_schema, mosque_policy_schema]:
        jsonschema.Draft202012Validator.check_schema(schema)
    example = json.loads((root / 'examples/synthetic-prayer-snapshot.json').read_text(encoding='utf-8'))
    jsonschema.Draft202012Validator(snapshot_schema).validate(example)
    trust_bundle = json.loads((root / 'fixtures/verification/phase1-trust-bundle.json').read_text(encoding='utf-8'))
    trust_schema = next(schema for schema in publication_schemas if schema['title'] == 'Snapshot publication public trust bundle')
    jsonschema.Draft202012Validator(trust_schema).validate(trust_bundle)
    pilot = root / 'fixtures/pilot/ulyanovsk-2026'
    jsonschema.Draft202012Validator(approval_trust_schema).validate(
        json.loads((pilot / 'approver-trust-bundle.json').read_text(encoding='utf-8'))
    )
    jsonschema.Draft202012Validator(approval_receipt_schema).validate(
        json.loads((pilot / 'approval-receipt.json').read_text(encoding='utf-8'))
    )
    jsonschema.Draft202012Validator(mosque_policy_schema).validate(
        json.loads((pilot / 'mosque-prayer-policy.json').read_text(encoding='utf-8'))
    )
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
        relative = path.relative_to(root)
        expected_sha256 = large_file_sha256_allowlist.get(relative)
        if expected_sha256 is None:
            raise SystemExit(f'Unexpected file over 5 MiB: {relative}')
        digest = hashlib.sha256()
        with path.open('rb') as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b''):
                digest.update(chunk)
        if digest.hexdigest() != expected_sha256:
            raise SystemExit(f'Authorized large fixture SHA-256 mismatch: {relative}')

summary = json.loads((root / 'research/evidence/islamapp-1.6.2-static-summary.json').read_text(encoding='utf-8'))
san = summary.get('sanitization', {})
for field in ('raw_apk_included', 'raw_or_decrypted_competitor_dataset_included', 'embedded_secret_included', 'proprietary_asset_included'):
    if san.get(field) is not False:
        raise SystemExit(f'Sanitization field must be false: {field}')

print('docs-check: PASS')
PYDOCS
