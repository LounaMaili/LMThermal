#!/usr/bin/env python3
"""Verify that editorial acceptance preserves the exact owner-reviewed rule body."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
REVIEWED = '2c18401af7ef45319a7c025f136d94939e5bee03'


def main():
    """Compare immutable reviewed semantics and emit an honest documentation report."""
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    reviewed = subprocess.check_output(['git', 'show', REVIEWED + ':docs/LMTR_WIRE_V1_DRAFT.md'], cwd=ROOT, text=True)
    accepted = (ROOT/'docs/LMTR_WIRE_V1.md').read_text()
    original_body = reviewed[reviewed.index('## 2. Identity'):reviewed.index('## 17. Rule provenance')]
    accepted_body = accepted[accepted.index('## 2. Identity'):accepted.index('## 17. Acceptance')]
    assert accepted_body.replace('(lmtr-v1/schema.json)', '(lmtr-r1b/schema.json)') == original_body
    old_schema = json.loads(subprocess.check_output(['git', 'show', REVIEWED + ':docs/lmtr-r1b/schema.json'], cwd=ROOT))
    new_schema = json.loads((ROOT/'docs/lmtr-v1/schema.json').read_text())
    assert old_schema['$defs'] == new_schema['$defs']
    assert (ROOT/'docs/LMTR_WIRE_V1_DRAFT.md').read_text() == reviewed
    result = {'status':'EDITORIAL_ACCEPTANCE_CHECKS_PASS_NOT_IMPLEMENTATION_CONFORMANCE',
              'reviewed_revision':'r1b-review-2', 'reviewed_commit':REVIEWED,
              'acceptance_date':'2026-10-13', 'normative_sections_2_through_16_unchanged':True,
              'shape_definitions_unchanged':len(new_schema['$defs']), 'original_review_preserved':True,
              'normative_sha256':hashlib.sha256(accepted.encode()).hexdigest(),
              'accepted_schema_sha256':hashlib.sha256((ROOT/'docs/lmtr-v1/schema.json').read_bytes()).hexdigest(),
              'scope':'Editorial identity/status/reference changes only; no canonical-byte/runtime/platform certification.'}
    if args.report:
        args.report.write_text(json.dumps(result, indent=2)+'\n')
    print('Acceptance checks pass: exact reviewed §§2–16 and', len(new_schema['$defs']), 'shape definitions preserved.')


if __name__ == '__main__':
    main()
