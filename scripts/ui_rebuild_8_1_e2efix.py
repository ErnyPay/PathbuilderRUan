#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TEST=ROOT/'ci/final_product_e2e.sh'

s=TEST.read_text(encoding='utf-8')
s=s.replace("versionName=8.0.0", "versionName=8.1.0")

# Compact 8.1 picker rows render primary label and metadata on one line.
# Keep E2E assertions semantic by extracting only the primary name.
s=s.replace('SPELL_NAME="${SPELL_TEXT%%$\'\\n\'*}"', 'SPELL_NAME="${SPELL_TEXT%%  ·  *}"')
s=s.replace('ITEM_NAME="${ITEM_TEXT%%$\'\\n\'*}"', 'ITEM_NAME="${ITEM_TEXT%%  ·  *}"')
s=s.replace('COND_NAME="${COND_TEXT%%$\'\\n\'*}"', 'COND_NAME="${COND_TEXT%%  ·  *}"')

# 8.1 moved the PLAY tab strip upward: its actual bounds are roughly y=335..413.
# Swipe through the centre of the strip instead of the legacy y=350 edge, so
# off-screen Pets/Effects tabs remain reachable without widening the visual UI.
s=s.replace('adb shell input swipe 990 350 230 350 350', 'adb shell input swipe 990 375 230 375 300')
s=s.replace('adb shell input swipe 230 350 990 350 350', 'adb shell input swipe 230 375 990 375 300')

# final_product_8_0_e2efix.py has already expanded the original am-start block.
# Replace that generated section by markers rather than matching its exact text.
start=s.find('adb logcat -c')
end=s.find("assert_text 'НОВЫЙ ПЕРСОНАЖ'", start)
if start < 0 or end < 0:
    raise SystemExit('generated 8.1 E2E home startup markers not found')
end += len("assert_text 'НОВЫЙ ПЕРСОНАЖ'")

new=r'''adb logcat -c
adb shell am force-stop "$PKG" || true
launch_gran_home(){
  local n out
  for n in 1 2 3 4; do
    log "launch FrontPageActivity attempt $n"
    out="$(adb shell am start -W -n "$PKG/$ACT.FrontPageActivity" 2>&1 || true)"
    printf '%s\n' "$out" | tee "ci/e2e/home-start-$n.txt"
    sleep 2
    if adb shell dumpsys activity activities | grep -E -q "mResumedActivity:.*$PKG/$ACT.FrontPageActivity|topResumedActivity=.*$PKG/$ACT.FrontPageActivity"; then
      echo "[E2E] home pid=$(adb shell pidof "$PKG" | tr -d '\r')"
      return 0
    fi
    # dump_ui dismisses only the emulator's Quickstep ANR. A Gran ANR remains a
    # hard failure. If the launcher returns after dismissal, explicitly retry Gran.
    dump_ui >/dev/null 2>&1 || true
    adb shell input keyevent 82 >/dev/null 2>&1 || true
    sleep 1
  done
  echo 'Gran FrontPageActivity did not become resumed after launcher recovery' >&2
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' >&2 || true
  return 1
}
launch_gran_home
assert_text 'НОВЫЙ ПЕРСОНАЖ' '''

s=s[:start]+new+s[end:]
TEST.write_text(s,encoding='utf-8')
print('Gran 2e 8.1 E2E version, compact labels, tab geometry and launcher recovery applied')
