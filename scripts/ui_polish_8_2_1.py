#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/ru/gran/edge2e'
changed=[]
for path in JAVA.glob('*.java'):
    s=path.read_text(encoding='utf-8')
    n=s.replace('curriculum-slot','ячейка учебного плана')
    if n != s:
        path.write_text(n,encoding='utf-8')
        changed.append(path.name)
if not changed:
    raise SystemExit('8.2.1 curriculum-slot source was not found after generation')

gradle=ROOT/'app/build.gradle'
s=gradle.read_text(encoding='utf-8').replace('versionCode 820','versionCode 821').replace("versionName '8.2.0'","versionName '8.2.1'")
if "versionName '8.2.1'" not in s:
    raise SystemExit('8.2.1 version bump was not applied')
gradle.write_text(s,encoding='utf-8')
print('Gran 2e 8.2.1 removed internal curriculum-slot label from: '+', '.join(changed))
