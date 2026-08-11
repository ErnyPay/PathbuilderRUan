#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PLAY=ROOT/'app/src/main/java/ru/gran/edge2e/ReferencePlayActivity.java'
s=PLAY.read_text(encoding='utf-8')
old='TextView equip = actionRow("Броня", item.id.equals(stats.equippedArmorId) ? "ЭКИПИРОВАНО" : "экипировать");\n            equip.setOnClickListener'
new='TextView equip = actionRow("Броня", item.id.equals(stats.equippedArmorId) ? "ЭКИПИРОВАНО" : "экипировать");\n            equip.setContentDescription("item-equip-armor");\n            equip.setOnClickListener'
if old not in s:
    raise SystemExit('Gran 9.1 armor action marker not found')
s=s.replace(old,new,1)
PLAY.write_text(s,encoding='utf-8')
print('Gran 2e 9.1 armor equip action id restored in compact item dialog')
