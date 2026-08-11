#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
TEST=ROOT/'ci/final_product_e2e.sh'
s=TEST.read_text(encoding='utf-8')
s=s.replace('versionName=9.0.0','versionName=9.1.0')
if 'versionName=9.1.0' not in s:
    raise SystemExit('Gran 9.1 E2E version check was not applied')
TEST.write_text(s,encoding='utf-8')
print('Gran 2e 9.1 E2E bound to specialized reference pages')
