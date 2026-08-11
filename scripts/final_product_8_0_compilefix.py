#!/usr/bin/env python3
from pathlib import Path
import runpy

ROOT=Path(__file__).resolve().parents[1]
SETUP=ROOT/'app/src/main/java/ru/gran/edge2e/CharacterSetupActivity.java'
PLAY=ROOT/'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java'

def replace(path, old, new):
    s=path.read_text(encoding='utf-8')
    if old not in s and new not in s:
        raise SystemExit(f'compile fix target not found in {path}')
    s=s.replace(old,new)
    path.write_text(s,encoding='utf-8')

def main():
    replace(SETUP,'TextView r=selectRow(item,shown);','View r=selectRow(item,shown);')
    replace(PLAY,'View parent = content == null ? null : content.getParent();','android.view.ViewParent parent = content == null ? null : content.getParent();')
    replace(PLAY,
            'TextView t = tab(spec[0], spec[1].equals(screen)); String target = spec[1];\n            t.setOnClickListener(v -> { screen = target; render(); }); nav.addView(t, wrapWrap(dp(2)));',
            'TextView t = tab(spec[0], spec[1].equals(screen)); String target = spec[1];\n            t.setContentDescription("play-tab-" + target);\n            t.setOnClickListener(v -> { screen = target; render(); }); nav.addView(t, wrapWrap(dp(2)));')
    runpy.run_path(str(ROOT/'scripts/final_product_8_0_e2efix.py'),run_name='__main__')
    print('Gran 2e 8.0 Java compatibility, PLAY identity and E2E navigation fixes applied')

if __name__=='__main__': main()
