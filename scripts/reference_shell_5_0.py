#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FRONT = ROOT / 'app/src/main/java/ru/gran/edge2e/FrontPageActivity.java'
PLAY = ROOT / 'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java'


def replace_all(path: Path, replacements):
    s = path.read_text(encoding='utf-8')
    changed = False
    for old, new in replacements:
        if old in s:
            s = s.replace(old, new)
            changed = True
    path.write_text(s, encoding='utf-8')
    return changed


def main():
    replace_all(FRONT, [
        ('MainActivityV3.class', 'ReferenceBuildActivity.class'),
        ('MainActivityV2.class', 'ReferencePlayActivity.class'),
    ])
    replace_all(PLAY, [
        ('MainActivityV3.class', 'ReferenceBuildActivity.class'),
    ])
    print('Applied Gran 5.0 reference BUILD/PLAY routing')


if __name__ == '__main__':
    main()
