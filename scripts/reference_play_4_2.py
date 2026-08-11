#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
V3 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV3.java'


def main():
    s = V3.read_text(encoding='utf-8')
    if 'ReferencePlayActivity.class' not in s:
        if 'MainActivityV2.class' not in s:
            raise SystemExit('Gran 4.2 PLAY routing anchor not found in MainActivityV3')
        s = s.replace('MainActivityV2.class', 'ReferencePlayActivity.class')
    V3.write_text(s, encoding='utf-8')
    print('Applied Gran 4.2 reference PLAY routing')


if __name__ == '__main__':
    main()
