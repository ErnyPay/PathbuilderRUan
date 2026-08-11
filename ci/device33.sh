#!/usr/bin/env bash
set -euo pipefail

APK=app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb install -r "$APK"
adb shell dumpsys package ru.gran.gran2e | grep 'versionName=3.3.0'

# First launch initializes rules DB.
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV3
sleep 10
test -n "$(adb shell pidof ru.gran.gran2e | tr -d '\r')" || { adb logcat -d -v time | tail -n 900; exit 1; }

# Combat regression from 3.1.
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen attack
for i in 1 2 3 4 5; do
  sleep 2
  adb shell uiautomator dump /sdcard/attack33.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/attack33.xml > attack33.xml 2>/dev/null || true
  grep -q 'Критический урон' attack33.xml 2>/dev/null && break || true
done
grep -q 'MAP 0 /' attack33.xml
grep -q 'Критический урон' attack33.xml

# Wizard prepared regression from 3.2.
adb push ci-wizard.xml /data/local/tmp/ci-character.xml >/dev/null
adb shell run-as ru.gran.gran2e mkdir -p shared_prefs
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-character.xml shared_prefs/gran2e_character.xml
adb shell run-as ru.gran.gran2e rm -f shared_prefs/gran2e_spellcasting_v32.xml
adb shell am force-stop ru.gran.gran2e
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen spells
sleep 5
adb shell uiautomator dump /sdcard/wizard33.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/wizard33.xml > wizard33.xml 2>/dev/null || true
grep -q 'Волшебник' wizard33.xml
grep -q 'подготовленные' wizard33.xml
grep -q 'арканная' wizard33.xml

# Human + Acolyte + Additional Lore fixture.
adb push ci-knowledge-character.xml /data/local/tmp/ci-character.xml >/dev/null
adb push ci-knowledge-state.xml /data/local/tmp/ci-knowledge.xml >/dev/null
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-character.xml shared_prefs/gran2e_character.xml
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-knowledge.xml shared_prefs/gran2e_knowledge_v33.xml
adb shell am force-stop ru.gran.gran2e
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen skills
sleep 5
adb shell uiautomator dump /sdcard/knowledge33-top.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/knowledge33-top.xml > knowledge33-top.xml 2>/dev/null || true
grep -q 'ЯЗЫКИ' knowledge33-top.xml || { adb logcat -d -v time | tail -n 700; cat knowledge33-top.xml; exit 1; }
grep -q 'Всеобщий' knowledge33-top.xml
grep -q 'Драконий' knowledge33-top.xml
grep -q '1 / 1' knowledge33-top.xml
adb exec-out screencap -p > gran2e-33-languages.png

: > knowledge33-all.xml
for i in 1 2 3 4 5 6; do
  adb shell uiautomator dump /sdcard/knowledge33.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/knowledge33.xml > knowledge33.xml 2>/dev/null || true
  cat knowledge33.xml >> knowledge33-all.xml
  adb shell input swipe 540 1750 540 850 450
  sleep 1
done
grep -q 'LORE' knowledge33-all.xml
grep -q 'Военная история' knowledge33-all.xml
grep -q 'Эксперт' knowledge33-all.xml
# Acolyte must contribute its automatic Scribing Lore; accept Russian or canonical fallback.
grep -Eq 'Scribing Lore|Писц|Письм' knowledge33-all.xml || { cat knowledge33-all.xml; exit 1; }
adb exec-out screencap -p > gran2e-33-lore.png
