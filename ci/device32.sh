#!/usr/bin/env bash
set -euo pipefail

APK=app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb install -r "$APK"
adb shell dumpsys package ru.gran.gran2e | grep 'versionName=3.2.0'

adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV3
sleep 10
test -n "$(adb shell pidof ru.gran.gran2e | tr -d '\r')" || { adb logcat -d -v time | tail -n 900; exit 1; }

# 3.1 combat regression.
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen attack
for i in 1 2 3 4 5; do
  sleep 2
  adb shell uiautomator dump /sdcard/attack32.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/attack32.xml > attack32.xml 2>/dev/null || true
  grep -q 'Критический урон' attack32.xml 2>/dev/null && break || true
done
grep -q 'MAP 0 /' attack32.xml
grep -q 'Критический урон' attack32.xml

# Wizard 3: standard prepared casting + dictionary integrity.
adb push ci-wizard.xml /data/local/tmp/ci-character.xml >/dev/null
adb shell run-as ru.gran.gran2e mkdir -p shared_prefs
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-character.xml shared_prefs/gran2e_character.xml
adb shell run-as ru.gran.gran2e rm -f shared_prefs/gran2e_spellcasting_v32.xml
adb shell am force-stop ru.gran.gran2e
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen spells
sleep 5
adb shell uiautomator dump /sdcard/wizard32-top.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/wizard32-top.xml > wizard32-top.xml 2>/dev/null || true
grep -q 'Волшебник' wizard32-top.xml || { adb logcat -d -v time | tail -n 700; cat wizard32-top.xml; exit 1; }
grep -q 'подготовленные' wizard32-top.xml
grep -q 'арканная' wizard32-top.xml
if grep -Eq '@UUID|@Damage|@Check' wizard32-top.xml; then cat wizard32-top.xml; exit 1; fi
adb exec-out screencap -p > gran2e-32-wizard-top.png
: > wizard32-all.xml
for i in 1 2 3 4 5 6 7; do
  adb shell uiautomator dump /sdcard/wizard32-slots.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/wizard32-slots.xml > wizard32-slots.xml 2>/dev/null || true
  cat wizard32-slots.xml >> wizard32-all.xml
  adb shell input swipe 540 1750 540 850 450
  sleep 1
done
grep -q 'Ранг 1 • 3 слотов' wizard32-all.xml || { cat wizard32-all.xml; exit 1; }
grep -q 'Ранг 2 • 2 слотов' wizard32-all.xml || { cat wizard32-all.xml; exit 1; }
grep -q 'Школьный слот ранга 1' wizard32-all.xml || { cat wizard32-all.xml; exit 1; }
adb exec-out screencap -p > gran2e-32-wizard-slots.png

# Bard 3: standard spontaneous casting + dictionary integrity.
adb push ci-bard.xml /data/local/tmp/ci-character.xml >/dev/null
adb shell run-as ru.gran.gran2e cp /data/local/tmp/ci-character.xml shared_prefs/gran2e_character.xml
adb shell run-as ru.gran.gran2e rm -f shared_prefs/gran2e_spellcasting_v32.xml
adb shell am force-stop ru.gran.gran2e
adb shell am start -n ru.gran.gran2e/ru.gran.edge2e.MainActivityV2 --es screen spells
sleep 5
adb shell uiautomator dump /sdcard/bard32-top.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/bard32-top.xml > bard32-top.xml 2>/dev/null || true
grep -q 'Бард' bard32-top.xml || { adb logcat -d -v time | tail -n 700; cat bard32-top.xml; exit 1; }
grep -q 'спонтанные' bard32-top.xml
grep -q 'оккультная' bard32-top.xml
if grep -Eq '@UUID|@Damage|@Check' bard32-top.xml; then cat bard32-top.xml; exit 1; fi
adb exec-out screencap -p > gran2e-32-bard-top.png
: > bard32-all.xml
for i in 1 2 3 4 5 6 7; do
  adb shell uiautomator dump /sdcard/bard32-slots.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/bard32-slots.xml > bard32-slots.xml 2>/dev/null || true
  cat bard32-slots.xml >> bard32-all.xml
  adb shell input swipe 540 1750 540 850 450
  sleep 1
done
grep -q 'Ранг 1 • слоты 3 / 3' bard32-all.xml || { cat bard32-all.xml; exit 1; }
grep -q 'Ранг 2 • слоты 2 / 2' bard32-all.xml || { cat bard32-all.xml; exit 1; }
grep -q 'Signature spell' bard32-all.xml || { cat bard32-all.xml; exit 1; }
adb exec-out screencap -p > gran2e-32-bard-slots.png
