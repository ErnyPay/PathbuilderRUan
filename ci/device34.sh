#!/usr/bin/env bash
set -euo pipefail
APK=app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb install -r "$APK"
adb shell dumpsys package ru.gran.gran2e | grep 'versionName=3.4.0'
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV3
sleep 10
test -n "$(adb shell pidof ru.gran.gran2e | tr -d '\r')" || { adb logcat -d -v time | tail -n 900; exit 1; }
adb shell run-as ru.gran.gran2e mkdir -p shared_prefs

# Combat regression.
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen attack
for i in 1 2 3 4 5; do sleep 2; adb shell uiautomator dump /sdcard/attack34.xml >/dev/null 2>&1 || true; adb shell cat /sdcard/attack34.xml > attack34.xml 2>/dev/null || true; grep -q 'Критический урон' attack34.xml 2>/dev/null && break || true; done
grep -q 'MAP 0 /' attack34.xml
grep -q 'Критический урон' attack34.xml

# Prepared casting regression.
adb push ci-wizard.xml /data/local/tmp/ci-character.xml >/dev/null
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-character.xml shared_prefs/gran2e_character.xml
adb shell run-as ru.gran.gran2e rm -f shared_prefs/gran2e_spellcasting_v32.xml
adb shell am force-stop ru.gran.gran2e
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen spells
sleep 5
adb shell uiautomator dump /sdcard/wizard34.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/wizard34.xml > wizard34.xml 2>/dev/null || true
grep -q 'Волшебник' wizard34.xml
grep -q 'подготовленные' wizard34.xml

# Knowledge regression.
adb push ci-knowledge-character.xml /data/local/tmp/ci-character.xml >/dev/null
adb push ci-knowledge-state.xml /data/local/tmp/ci-knowledge.xml >/dev/null
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-character.xml shared_prefs/gran2e_character.xml
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-knowledge.xml shared_prefs/gran2e_knowledge_v33.xml
adb shell am force-stop ru.gran.gran2e
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen skills
sleep 4
adb shell uiautomator dump /sdcard/knowledge34.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/knowledge34.xml > knowledge34.xml 2>/dev/null || true
grep -q 'Всеобщий' knowledge34.xml
grep -q 'Драконий' knowledge34.xml

# 3 Bulk of swords in worn backpack: first 2 Bulk ignored => total counted 1 Bulk.
adb push ci-bulk-character.xml /data/local/tmp/ci-character.xml >/dev/null
adb push ci-bulk-normal.xml /data/local/tmp/ci-inventory.xml >/dev/null
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-character.xml shared_prefs/gran2e_character.xml
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-inventory.xml shared_prefs/gran2e_inventory_v2.xml
adb shell am force-stop ru.gran.gran2e
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen equipment
sleep 5
adb shell uiautomator dump /sdcard/bulk34-normal.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/bulk34-normal.xml > bulk34-normal.xml 2>/dev/null || true
grep -q 'СНАРЯЖЕНИЕ' bulk34-normal.xml || { adb logcat -d -v time | tail -n 700; cat bulk34-normal.xml; exit 1; }
grep -q 'НОРМА' bulk34-normal.xml
grep -q 'Рюкзак' bulk34-normal.xml
grep -q 'содержимое 3 / 4' bulk34-normal.xml
grep -q 'считается 1' bulk34-normal.xml
grep -q 'Длинный меч ×3' bulk34-normal.xml
grep -q 'в: Рюкзак' bulk34-normal.xml
adb exec-out screencap -p > gran2e-34-bulk-normal.png

# Overfill the same backpack: ignore benefit disappears completely.
adb push ci-bulk-overflow.xml /data/local/tmp/ci-inventory.xml >/dev/null
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-inventory.xml shared_prefs/gran2e_inventory_v2.xml
adb shell am force-stop ru.gran.gran2e
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen equipment
sleep 4
adb shell uiautomator dump /sdcard/bulk34-overflow.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/bulk34-overflow.xml > bulk34-overflow.xml 2>/dev/null || true
grep -q 'ПЕРЕПОЛНЕН' bulk34-overflow.xml || { cat bulk34-overflow.xml; exit 1; }
grep -q 'содержимое 5 / 4' bulk34-overflow.xml
grep -q 'считается 5' bulk34-overflow.xml
adb exec-out screencap -p > gran2e-34-bulk-overflow.png
