#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TEST=ROOT/'ci/final_product_e2e.sh'

s=TEST.read_text(encoding='utf-8')
s=s.replace("versionName=8.0.0", "versionName=8.1.0")

old='''adb install "$APK" >/dev/null\nadb shell dumpsys package "$PKG" | grep -q 'versionName=8.1.0'\nadb shell am start -n "$PKG/$ACT.FrontPageActivity" >/dev/null\nsleep 2\nassert_text 'НОВЫЙ ПЕРСОНАЖ'\n'''
new='''adb install "$APK" >/dev/null\nadb shell dumpsys package "$PKG" | grep -q 'versionName=8.1.0'\n\nlaunch_gran_home(){\n  local n out\n  for n in 1 2 3 4; do\n    log "launch FrontPageActivity attempt $n"\n    out="$(adb shell am start -W -n "$PKG/$ACT.FrontPageActivity" 2>&1 || true)"\n    printf '%s\\n' "$out"\n    sleep 2\n    if adb shell dumpsys activity activities | grep -E -q "mResumedActivity:.*$PKG/$ACT.FrontPageActivity|topResumedActivity=.*$PKG/$ACT.FrontPageActivity"; then\n      return 0\n    fi\n    # dump_ui already dismisses only the emulator's Quickstep ANR. It never\n    # suppresses an ANR belonging to Gran. After dismissal, explicitly retry\n    # our activity instead of assuming the launcher will return to the app.\n    dump_ui >/dev/null 2>&1 || true\n    adb shell input keyevent 82 >/dev/null 2>&1 || true\n    sleep 1\n  done\n  echo 'Gran FrontPageActivity did not become resumed after launcher recovery' >&2\n  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' >&2 || true\n  return 1\n}\n\nlaunch_gran_home\nassert_text 'НОВЫЙ ПЕРСОНАЖ'\n'''
if old not in s:
    raise SystemExit('8.1 E2E startup block not found')
s=s.replace(old,new)
TEST.write_text(s,encoding='utf-8')
print('Gran 2e 8.1 E2E version and launcher recovery applied')
