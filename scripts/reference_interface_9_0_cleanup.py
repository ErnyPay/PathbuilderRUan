#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/ru/gran/edge2e'
changed=[]
replacements={
    'дополнительный ячейка учебного плана':'дополнительная ячейка учебного плана',
    'дополнительный ячейка':'дополнительная ячейка',
}
for path in JAVA.glob('*.java'):
    s=path.read_text(encoding='utf-8')
    old=s
    for a,b in replacements.items():
        s=s.replace(a,b)
    if s!=old:
        path.write_text(s,encoding='utf-8')
        changed.append(path.name)
print('Gran 2e 9.0 visible reference-interface wording cleanup applied:', ', '.join(changed) or 'no-op')
