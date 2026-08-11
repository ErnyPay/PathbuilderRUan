#!/usr/bin/env python3
from pathlib import Path
import runpy

ROOT=Path(__file__).resolve().parents[1]
SETUP=ROOT/'app/src/main/java/ru/gran/edge2e/CharacterSetupActivity.java'
PLAY=ROOT/'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java'
TEST=ROOT/'ci/final_product_e2e.sh'

def replace(path, old, new):
    s=path.read_text(encoding='utf-8')
    if old not in s and new not in s:
        raise SystemExit(f'compile fix target not found in {path}')
    s=s.replace(old,new)
    path.write_text(s,encoding='utf-8')

def patch_e2e():
    s=TEST.read_text(encoding='utf-8')

    old_dump=r'''dump_ui(){
  local n
  for n in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/gran-window.xml >/dev/null 2>&1 || true
    adb shell cat /sdcard/gran-window.xml > ci/e2e/window.xml 2>/dev/null || true
    if grep -q '<hierarchy' ci/e2e/window.xml 2>/dev/null; then return 0; fi
    sleep 1
  done
  echo 'Could not dump UI' >&2; return 1
}
'''
    new_dump=r'''dump_ui(){
  local n
  for n in 1 2 3 4 5; do
    adb shell rm -f /sdcard/gran-window.xml >/dev/null 2>&1 || true
    : > ci/e2e/window.xml
    if adb shell uiautomator dump /sdcard/gran-window.xml >/dev/null 2>&1; then
      adb shell cat /sdcard/gran-window.xml > ci/e2e/window.xml 2>/dev/null || true
      if grep -q '<hierarchy' ci/e2e/window.xml 2>/dev/null; then return 0; fi
    fi
    sleep 1
  done
  echo 'Could not dump fresh UI' >&2; return 1
}
'''
    if old_dump in s:
        s=s.replace(old_dump,new_dump)
    elif new_dump not in s:
        raise SystemExit('fresh UI dump target not found')

    start=s.find('text_desc(){')
    end=s.find('first_text_contains(){',start)
    if start < 0 or end < 0:
        raise SystemExit('text_desc E2E section not found')
    robust_text_desc=r'''text_desc(){
  local desc="$1" i out
  for i in $(seq 1 8); do
    dump_ui
    if out="$(python3 - "$desc" <<'PY'
import sys,xml.etree.ElementTree as ET
needle=sys.argv[1];root=ET.parse('ci/e2e/window.xml').getroot()
def first_text(node):
    t=node.attrib.get('text','').strip()
    if t:
        return t
    for child in node:
        t=first_text(child)
        if t:
            return t
    return ''
for n in root.iter('node'):
    if n.attrib.get('content-desc')==needle:
        text=first_text(n)
        if text:
            print(text);sys.exit(0)
        sys.exit(3)
sys.exit(2)
PY
)"; then printf '%s\n' "$out"; return 0; fi
    sleep 1
  done
  echo "Timed out reading content-desc: $desc" >&2; return 2
}
'''
    s=s[:start]+robust_text_desc+'\n'+s[end:]
    TEST.write_text(s,encoding='utf-8')

def main():
    replace(SETUP,'TextView r=selectRow(item,shown);','View r=selectRow(item,shown);')
    replace(PLAY,'View parent = content == null ? null : content.getParent();','android.view.ViewParent parent = content == null ? null : content.getParent();')
    replace(PLAY,
            'TextView t = tab(spec[0], spec[1].equals(screen)); String target = spec[1];\n            t.setOnClickListener(v -> { screen = target; render(); }); nav.addView(t, wrapWrap(dp(2)));',
            'TextView t = tab(spec[0], spec[1].equals(screen)); String target = spec[1];\n            t.setContentDescription("play-tab-" + target);\n            t.setOnClickListener(v -> { screen = target; render(); }); nav.addView(t, wrapWrap(dp(2)));')
    runpy.run_path(str(ROOT/'scripts/final_product_8_0_e2efix.py'),run_name='__main__')
    patch_e2e()
    print('Gran 2e 8.0 Java compatibility, PLAY identity and fresh recursive E2E reads applied')

if __name__=='__main__': main()
