#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
V3 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV3.java'
PLAY = ROOT / 'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java'


def main():
    s = V3.read_text(encoding='utf-8')
    if 'ReferencePlayActivity.class' not in s:
        if 'MainActivityV2.class' not in s:
            raise SystemExit('Gran 4.2 PLAY routing anchor not found in MainActivityV3')
        s = s.replace('MainActivityV2.class', 'ReferencePlayActivity.class')
    V3.write_text(s, encoding='utf-8')

    play = PLAY.read_text(encoding='utf-8')
    old_parent = 'View parent = content == null ? null : content.getParent();'
    new_parent = 'android.view.ViewParent parent = content == null ? null : content.getParent();'
    if old_parent in play:
        play = play.replace(old_parent, new_parent, 1)
    elif new_parent not in play:
        raise SystemExit('Gran 4.2 PLAY parent anchor not found')

    old_speed = 'DerivedStats.speed(stats, ancestryItem(), equippedArmor())'
    new_speed = 'DerivedStats.speed(state, stats, ancestryItem(), equippedArmor())'
    if old_speed in play:
        play = play.replace(old_speed, new_speed, 1)
    elif new_speed not in play:
        raise SystemExit('Gran 4.2 PLAY speed anchor not found')

    PLAY.write_text(play, encoding='utf-8')
    print('Applied Gran 4.2 reference PLAY routing and runtime API compatibility')


if __name__ == '__main__':
    main()
