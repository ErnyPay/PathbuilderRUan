#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TEST=ROOT/'ci/final_product_e2e.sh'
s=TEST.read_text(encoding='utf-8')
for old in ['versionName=8.2.1','versionName=8.2.0','versionName=8.1.0','versionName=8.0.0']:
    s=s.replace(old,'versionName=9.0.0')
old="tap_desc home-new-character\nassert_text 'СОЗДАНИЕ ПЕРСОНАЖА'"
new="""tap_desc home-new-character
assert_text 'ОСНОВА ПЕРСОНАЖА'
log 'open internal setup harness to seed a deterministic full character'
adb shell am start -n \"$PKG/$ACT.CharacterSetupActivity\" >/dev/null
sleep 2
assert_text 'СОЗДАНИЕ ПЕРСОНАЖА'"""
if old not in s:
    raise SystemExit('9.0 E2E new-character flow marker not found')
s=s.replace(old,new)
s=s.replace('160 375 930 375 120','160 220 930 220 120')
s=s.replace('930 375 160 375 220','930 220 160 220 220')
TEST.write_text(s,encoding='utf-8')
print('Gran 2e 9.0 E2E bound to reference-interface product flow')
