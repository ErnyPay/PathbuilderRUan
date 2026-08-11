#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TEST=ROOT/'ci/final_product_e2e.sh'
s=TEST.read_text(encoding='utf-8')
for old in ['versionName=8.2.1','versionName=8.2.0','versionName=8.1.0','versionName=8.0.0']:
    s=s.replace(old,'versionName=9.0.0')

# 9.0 reference-style rows put metadata on the next line. Earlier 8.1 compact
# generation changed these extractors to the one-line middle-dot form, so restore
# semantic primary-name extraction after all older E2E patches have run.
s=s.replace('SPELL_NAME="${SPELL_TEXT%%  ·  *}"', 'SPELL_NAME="${SPELL_TEXT%%$\'\\n\'*}"')
s=s.replace('ITEM_NAME="${ITEM_TEXT%%  ·  *}"', 'ITEM_NAME="${ITEM_TEXT%%$\'\\n\'*}"')
s=s.replace('COND_NAME="${COND_TEXT%%  ·  *}"', 'COND_NAME="${COND_TEXT%%$\'\\n\'*}"')

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

# The 9.0 PLAY HorizontalScrollView is actually [345,252]..[1072,324] on the
# Pixel 6 CI profile. Keep the gesture fully inside it so later tabs remain
# reachable while the level controls stay fixed at the left.
s=s.replace('160 375 930 375 120','400 288 1020 288 120')
s=s.replace('930 375 160 375 220','1020 288 400 288 220')
TEST.write_text(s,encoding='utf-8')
print('Gran 2e 9.0 E2E bound to multiline reference-interface flow and real tab strip')
