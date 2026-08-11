#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="ru.gran.gran2e"
ACT="ru.gran.edge2e"
mkdir -p ci/e2e

log(){ printf '[E2E] %s\n' "$*"; }
dump_ui(){
  local n
  for n in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/gran-window.xml >/dev/null 2>&1 || true
    adb shell cat /sdcard/gran-window.xml > ci/e2e/window.xml 2>/dev/null || true
    if grep -q '<hierarchy' ci/e2e/window.xml 2>/dev/null; then return 0; fi
    sleep 1
  done
  echo 'Could not dump UI' >&2; return 1
}
coord_desc(){
  local desc="$1"; dump_ui
  python3 - "$desc" <<'PY'
import re,sys,xml.etree.ElementTree as ET
needle=sys.argv[1]
root=ET.parse('ci/e2e/window.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('content-desc')==needle:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups());print((x1+x2)//2,(y1+y2)//2);sys.exit(0)
sys.exit(2)
PY
}
coord_text(){
  local text="$1"; dump_ui
  python3 - "$text" <<'PY'
import re,sys,xml.etree.ElementTree as ET
needle=sys.argv[1].casefold()
root=ET.parse('ci/e2e/window.xml').getroot()
exact=[]; contains=[]
for n in root.iter('node'):
    t=n.attrib.get('text',''); b=n.attrib.get('bounds','')
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',b)
    if not m: continue
    row=(n,t,m)
    if t.casefold()==needle: exact.append(row)
    elif needle in t.casefold(): contains.append(row)
arr=exact or contains
if not arr: sys.exit(2)
arr.sort(key=lambda r:(r[0].attrib.get('clickable')!='true', int(r[2].group(2))))
n,t,m=arr[0];x1,y1,x2,y2=map(int,m.groups());print((x1+x2)//2,(y1+y2)//2)
PY
}
tap_desc(){ local xy; xy="$(coord_desc "$1")"; log "tap desc $1 @ $xy"; adb shell input tap $xy; sleep 1; }
tap_text(){ local xy; xy="$(coord_text "$1")"; log "tap text $1 @ $xy"; adb shell input tap $xy; sleep 1; }
assert_text(){ local needle="$1"; dump_ui; python3 - "$needle" <<'PY'
import sys,xml.etree.ElementTree as ET
needle=sys.argv[1].casefold(); root=ET.parse('ci/e2e/window.xml').getroot()
if not any(needle in n.attrib.get('text','').casefold() for n in root.iter('node')): raise SystemExit(2)
PY
}
text_desc(){ local desc="$1"; dump_ui; python3 - "$desc" <<'PY'
import sys,xml.etree.ElementTree as ET
needle=sys.argv[1];root=ET.parse('ci/e2e/window.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('content-desc')==needle: print(n.attrib.get('text',''));sys.exit(0)
sys.exit(2)
PY
}
first_text_contains(){ local needle="$1"; dump_ui; python3 - "$needle" <<'PY'
import sys,xml.etree.ElementTree as ET
needle=sys.argv[1].casefold();root=ET.parse('ci/e2e/window.xml').getroot()
for n in root.iter('node'):
    t=n.attrib.get('text','')
    if needle in t.casefold(): print(t);sys.exit(0)
sys.exit(2)
PY
}
unresolved_choice_coord(){ dump_ui; python3 - <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse('ci/e2e/window.xml').getroot()
for n in root.iter('node'):
    d=n.attrib.get('content-desc','');t=n.attrib.get('text','')
    if d.startswith('setup-choice-') and 'ВЫБРАТЬ' in t:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups());print((x1+x2)//2,(y1+y2)//2);sys.exit(0)
sys.exit(2)
PY
}
screenshot(){ adb exec-out screencap -p > "ci/e2e/$1.png"; }

log 'install clean APK'
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$APK" >/dev/null
adb shell dumpsys package "$PKG" | grep -q 'versionName=8.0.0'
adb shell am start -n "$PKG/$ACT.FrontPageActivity" >/dev/null
sleep 2
assert_text 'НОВЫЙ ПЕРСОНАЖ'
screenshot 01-home

log 'create character through guided setup'
tap_desc home-new-character
assert_text 'СОЗДАНИЕ ПЕРСОНАЖА'
tap_desc setup-name
adb shell input text FinalHero
adb shell input keyevent 4
sleep 1
tap_desc setup-next
assert_text 'ВЫБЕРИ РОД'
tap_desc setup-result-0
tap_desc setup-next
assert_text 'ВЫБЕРИ НАСЛЕДИЕ'
tap_desc setup-result-0
tap_desc setup-next
assert_text 'ВЫБЕРИ ПРЕДЫСТОРИЮ'
tap_desc setup-result-0
tap_desc setup-next
assert_text 'ВЫБЕРИ КЛАСС'
tap_desc setup-search
adb shell input text Wizard
adb shell input keyevent 4
sleep 1
tap_desc setup-result-0
tap_desc setup-next
assert_text 'ОБЯЗАТЕЛЬНЫЕ ВЫБОРЫ'

log 'resolve all concrete mandatory rule choices'
resolved=0
for _ in $(seq 1 80); do
  if xy="$(unresolved_choice_coord 2>/dev/null)"; then
    adb shell input tap $xy; sleep 1
    tap_desc setup-option-0
    resolved=$((resolved+1))
  else
    break
  fi
done
if [ "$resolved" -ge 80 ]; then echo 'mandatory choice loop did not converge' >&2; exit 3; fi
assert_text 'Все доступные обязательные выборы заполнены.'
tap_desc setup-next
assert_text 'ГОТОВО К СБОРКЕ'
screenshot 02-review
tap_desc setup-next
assert_text 'ОСНОВА ПЕРСОНАЖА'
assert_text 'FinalHero'
screenshot 03-build

log 'enter PLAY and mutate HP'
tap_desc build-play
assert_text 'ПЕРСОНАЖ'
assert_text 'FinalHero'
tap_desc hp-minus-1
HP_LINE="$(first_text_contains '• ОЗ ')"
[ -n "$HP_LINE" ]
screenshot 04-play

log 'exercise Wizard prepared spellcasting'
tap_desc play-tab-spells
assert_text 'ЗАКЛИНАНИЯ'
assert_text 'КС заклинаний'
tap_desc spell-slot-1-0
SPELL_TEXT="$(text_desc spell-picker-0)"
SPELL_NAME="${SPELL_TEXT%%$'\n'*}"
[ -n "$SPELL_NAME" ]
tap_desc spell-picker-0
assert_text "$SPELL_NAME"
screenshot 05-spells

log 'add generic equipment'
tap_desc play-tab-gear
assert_text 'СНАРЯЖЕНИЕ'
tap_desc gear-add-item
ITEM_TEXT="$(text_desc equipment-picker-0)"
ITEM_NAME="${ITEM_TEXT%%$'\n'*}"
[ -n "$ITEM_NAME" ]
tap_desc equipment-picker-0
assert_text "$ITEM_NAME"

log 'add and equip armor via specialized catalog'
adb shell am start -n "$PKG/$ACT.ReferenceCatalogActivity" --es mode armor --ei maxLevel 20 >/dev/null
sleep 2
assert_text 'БРОНЯ'
ARMOR_TEXT="$(text_desc catalog-item-0)"
ARMOR_NAME="${ARMOR_TEXT%%$'\n'*}"
[ -n "$ARMOR_NAME" ]
tap_desc catalog-item-0
tap_text 'ДОБАВИТЬ'
adb shell input keyevent 4
sleep 2
assert_text "$ARMOR_NAME"
tap_text "$ARMOR_NAME"
assert_text 'БРОНЯ'
tap_desc item-equip-armor
assert_text 'ЭКИПИРОВАНО'
adb shell input keyevent 4
sleep 1
screenshot 06-gear

log 'add a condition'
tap_desc play-tab-effects
tap_desc effects-add-condition
COND_TEXT="$(text_desc condition-option-0)"
COND_NAME="${COND_TEXT%%$'\n'*}"
[ -n "$COND_NAME" ]
tap_desc condition-option-0
assert_text "$COND_NAME"
screenshot 07-effects

log 'create a pet and enter its editor'
tap_desc play-tab-pets
tap_desc pet-add-0
assert_text 'СПУТНИК'
assert_text 'Животный-компаньон'
adb shell input keyevent 4
sleep 1
assert_text 'Животный-компаньон'
screenshot 08-pets

log 'round-trip entire archive through clipboard'
tap_desc play-more
assert_text 'ИНСТРУМЕНТЫ'
tap_desc more-copy
adb shell input keyevent 4
sleep 1
tap_desc play-level-next
assert_text 'ур. 2'
tap_desc play-more
tap_desc more-paste
assert_text 'ИМПОРТ ИЗ БУФЕРА'
tap_desc more-paste-confirm
sleep 2
adb shell input keyevent 4
sleep 1
assert_text 'ур. 1'
RESTORED_HP="$(first_text_contains '• ОЗ ')"
[ "$RESTORED_HP" = "$HP_LINE" ]

log 'verify imported archive restored spells, gear, armor, effects and pets'
tap_desc play-tab-spells; assert_text "$SPELL_NAME"
tap_desc play-tab-gear; assert_text "$ITEM_NAME"; assert_text "$ARMOR_NAME"
tap_text "$ARMOR_NAME"; assert_text 'ЭКИПИРОВАНО'; adb shell input keyevent 4; sleep 1
tap_desc play-tab-effects; assert_text "$COND_NAME"
tap_desc play-tab-pets; assert_text 'Животный-компаньон'

log 'kill process and prove profile persistence after a cold restart'
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/$ACT.FrontPageActivity" >/dev/null
sleep 2
assert_text 'FinalHero'
screenshot 09-cold-home
tap_desc profile-play-0
assert_text 'FinalHero'
assert_text 'ур. 1'
COLD_HP="$(first_text_contains '• ОЗ ')"
[ "$COLD_HP" = "$HP_LINE" ]
tap_desc play-tab-spells; assert_text "$SPELL_NAME"
tap_desc play-tab-gear; assert_text "$ITEM_NAME"; assert_text "$ARMOR_NAME"
tap_desc play-tab-effects; assert_text "$COND_NAME"
tap_desc play-tab-pets; assert_text 'Животный-компаньон'
screenshot 10-cold-pets

log 'exercise remaining primary PLAY tabs'
tap_desc play-tab-attacks; assert_text 'АТАКИ'
tap_desc play-tab-defenses; assert_text 'ЗАЩИТА'
tap_desc play-tab-skills; assert_text 'НАВЫКИ'
tap_desc play-tab-feats; assert_text 'ФИТЫ'

dump_ui
cp ci/e2e/window.xml ci/e2e/final-window.xml
printf 'character=FinalHero\nhp=%s\nspell=%s\nitem=%s\narmor=%s\ncondition=%s\nmandatory_choices=%s\n' "$HP_LINE" "$SPELL_NAME" "$ITEM_NAME" "$ARMOR_NAME" "$COND_NAME" "$resolved" > ci/e2e/result.txt
log 'FINAL PRODUCT E2E PASSED'
