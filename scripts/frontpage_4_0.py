#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
V2 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV2.java'
V3 = ROOT / 'app/src/main/java/ru/gran/edge2e/MainActivityV3.java'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'Gran 4.0 front page patch missing anchor: {label}')
    return text.replace(old, new, 1)


def patch_v3() -> None:
    s = V3.read_text(encoding='utf-8')
    s = replace_once(s, 'TextView heroes = tab("ГЕРОИ", false);', 'TextView heroes = tab("ПЕРСОНАЖИ", false);', 'V3 character mode label')
    old = '''        heroes.setOnClickListener(v -> {\n            Intent i = new Intent(this, MainActivityV2.class); i.putExtra("screen", "profiles"); startActivity(i);\n        });'''
    new = '''        heroes.setOnClickListener(v -> {\n            Intent i = new Intent(this, FrontPageActivity.class); startActivity(i); finish();\n        });'''
    s = replace_once(s, old, new, 'V3 character manager action')
    V3.write_text(s, encoding='utf-8')


def patch_v2() -> None:
    s = V2.read_text(encoding='utf-8')
    s = replace_once(s, 'heroes = modeTab("ГЕРОИ", false);', 'heroes = modeTab("ПЕРСОНАЖИ", false);', 'V2 character mode label')
    s = replace_once(s, 'heroes.setOnClickListener(v -> { screen = "profiles"; render(); });', 'heroes.setOnClickListener(v -> { startActivity(new android.content.Intent(this, FrontPageActivity.class)); finish(); });', 'V2 character manager action')
    V2.write_text(s, encoding='utf-8')


if __name__ == '__main__':
    patch_v3()
    patch_v2()
    print('Applied Gran 4.0 character front page routing')
