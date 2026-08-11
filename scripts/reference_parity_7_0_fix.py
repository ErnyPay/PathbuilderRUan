#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAY = ROOT / 'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java'


def main():
    s = PLAY.read_text(encoding='utf-8')
    old = 'DerivedStats.speed(stats, ancestryItem(), equippedArmor())'
    new = 'DerivedStats.speed(state, stats, ancestryItem(), equippedArmor())'
    if old not in s:
        if new in s:
            print('Gran 7 speed compatibility already applied')
            return
        raise SystemExit('Gran 7 speed call anchor not found')
    PLAY.write_text(s.replace(old, new), encoding='utf-8')
    print('Fixed Gran 7 generated speed API call')


if __name__ == '__main__':
    main()
