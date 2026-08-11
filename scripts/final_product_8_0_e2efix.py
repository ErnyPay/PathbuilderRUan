#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TEST=ROOT/'ci/final_product_e2e.sh'


def section_replace(s, start_marker, end_marker, replacement):
    a=s.find(start_marker)
    b=s.find(end_marker,a)
    if a < 0 or b < 0: raise SystemExit(f'E2E helper section not found: {start_marker}')
    return s[:a]+replacement.rstrip()+'\n'+s[b:]


def main():
    s=TEST.read_text(encoding='utf-8')

    # Always leave useful diagnostics in the workspace and console on a failed user step.
    if 'failure_diag(){' not in s:
        marker="log(){ printf '[E2E] %s\\n' \"$*\"; }\n"
        diag=r'''failure_diag(){
  local code=$?
  set +e
  mkdir -p ci/e2e
  echo "[E2E-DIAG] exit=$code"
  echo "[E2E-DIAG] pid:"; adb shell pidof "$PKG" || true
  echo "[E2E-DIAG] resumed activity:"
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ru\.gran' | tail -n 40 || true
  dump_ui || true
  cp ci/e2e/window.xml ci/e2e/failure-window.xml 2>/dev/null || true
  echo "[E2E-DIAG] UI XML:"; cat ci/e2e/window.xml 2>/dev/null | head -c 24000 || true; echo
  adb exec-out screencap -p > ci/e2e/failure-screen.png 2>/dev/null || true
  echo "[E2E-DIAG] logcat:"
  adb logcat -d -t 500 | grep -E 'AndroidRuntime|FATAL EXCEPTION|ActivityTaskManager|ru\.gran|Gran' | tail -n 250 | tee ci/e2e/failure-logcat.txt || true
  exit "$code"
}
trap failure_diag ERR
'''
        if marker not in s: raise SystemExit('E2E log helper not found')
        s=s.replace(marker,marker+'\n'+diag+'\n')

    old_tap='tap_desc(){ local xy; xy="$(coord_desc "$1")"; log "tap desc $1 @ $xy"; adb shell input tap $xy; sleep 1; }\n'
    robust_tap=r'''tap_desc(){
  local desc="$1" xy i
  for i in $(seq 1 8); do
    if xy="$(coord_desc "$desc" 2>/dev/null)"; then
      log "tap desc $desc @ $xy"; adb shell input tap $xy; sleep 1; return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for content-desc: $desc" >&2
  dump_ui || true; adb exec-out screencap -p > "ci/e2e/timeout-${desc//\//_}.png" 2>/dev/null || true
  return 2
}
'''
    if old_tap in s: s=s.replace(old_tap,robust_tap)

    old_text='tap_text(){ local xy; xy="$(coord_text "$1")"; log "tap text $1 @ $xy"; adb shell input tap $xy; sleep 1; }\n'
    robust_text=r'''tap_text(){
  local needle="$1" xy i
  for i in $(seq 1 8); do
    if xy="$(coord_text "$needle" 2>/dev/null)"; then
      log "tap text $needle @ $xy"; adb shell input tap $xy; sleep 1; return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for text to tap: $needle" >&2
  dump_ui || true; return 2
}
'''
    if old_text in s: s=s.replace(old_text,robust_text)

    robust_assert=r'''assert_text(){
  local needle="$1" i
  for i in $(seq 1 8); do
    dump_ui
    if python3 - "$needle" <<'PY'
import sys,xml.etree.ElementTree as ET
needle=sys.argv[1].casefold(); root=ET.parse('ci/e2e/window.xml').getroot()
raise SystemExit(0 if any(needle in n.attrib.get('text','').casefold() for n in root.iter('node')) else 2)
PY
    then return 0; fi
    sleep 1
  done
  echo "Timed out waiting for text: $needle" >&2
  adb exec-out screencap -p > "ci/e2e/text-timeout.png" 2>/dev/null || true
  return 2
}
'''
    s=section_replace(s,'assert_text(){','text_desc(){',robust_assert)

    robust_text_desc=r'''text_desc(){
  local desc="$1" i out
  for i in $(seq 1 8); do
    dump_ui
    if out="$(python3 - "$desc" <<'PY'
import sys,xml.etree.ElementTree as ET
needle=sys.argv[1];root=ET.parse('ci/e2e/window.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('content-desc')==needle: print(n.attrib.get('text',''));sys.exit(0)
sys.exit(2)
PY
)"; then printf '%s\n' "$out"; return 0; fi
    sleep 1
  done
  echo "Timed out reading content-desc: $desc" >&2; return 2
}
'''
    s=section_replace(s,'text_desc(){','first_text_contains(){',robust_text_desc)

    robust_first=r'''first_text_contains(){
  local needle="$1" i out
  for i in $(seq 1 8); do
    dump_ui
    if out="$(python3 - "$needle" <<'PY'
import sys,xml.etree.ElementTree as ET
needle=sys.argv[1].casefold();root=ET.parse('ci/e2e/window.xml').getroot()
for n in root.iter('node'):
    t=n.attrib.get('text','')
    if needle in t.casefold(): print(t);sys.exit(0)
sys.exit(2)
PY
)"; then printf '%s\n' "$out"; return 0; fi
    sleep 1
  done
  echo "Timed out reading text fragment: $needle" >&2; return 2
}
'''
    s=section_replace(s,'first_text_contains(){','unresolved_choice_coord(){',robust_first)

    if 'tap_tab(){' not in s:
        marker=robust_text
        helper=r'''tap_tab(){
  local target="$1" desc="play-tab-$1" xy i
  for i in $(seq 1 8); do adb shell input swipe 160 330 930 330 120 >/dev/null; done
  sleep 1
  for i in $(seq 1 10); do
    if xy="$(coord_desc "$desc" 2>/dev/null)"; then
      log "tap tab $target @ $xy"; adb shell input tap $xy; sleep 1; return 0
    fi
    adb shell input swipe 930 330 160 330 220 >/dev/null
    sleep 1
  done
  echo "Could not reach PLAY tab: $target" >&2
  dump_ui || true
  cp ci/e2e/window.xml "ci/e2e/tab-failure-$target.xml" 2>/dev/null || true
  adb exec-out screencap -p > "ci/e2e/tab-failure-$target.png" 2>/dev/null || true
  return 2
}
'''
        if marker not in s: raise SystemExit('E2E tap_text helper not found after timing patch')
        s=s.replace(marker,marker+'\n'+helper+'\n')

    for target in ['spells','gear','effects','pets','attacks','defenses','skills','feats']:
        s=s.replace('tap_desc play-tab-'+target,'tap_tab '+target)

    # Replace the opaque home launch with a deterministic Android activity launch report.
    old=r'''adb shell am start -n "$PKG/$ACT.FrontPageActivity" >/dev/null
sleep 2
assert_text 'НОВЫЙ ПЕРСОНАЖ' '''.strip()
    new=r'''adb logcat -c
adb shell am force-stop "$PKG" || true
log 'launch FrontPageActivity with wait result'
adb shell am start -W -n "$PKG/$ACT.FrontPageActivity" | tee ci/e2e/home-start.txt
sleep 2
echo "[E2E] home pid=$(adb shell pidof "$PKG" | tr -d '\r')"
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ru\.gran' | tail -n 20 || true
assert_text 'НОВЫЙ ПЕРСОНАЖ' '''.strip()
    if old in s:
        s=s.replace(old,new)
    else:
        # Previous timing patch may already have expanded the sleep.
        old2=r'''adb shell am start -n "$PKG/$ACT.FrontPageActivity" >/dev/null
sleep 4
assert_text 'НОВЫЙ ПЕРСОНАЖ' '''.strip()
        if old2 not in s: raise SystemExit('E2E home startup block not found')
        s=s.replace(old2,new)

    TEST.write_text(s,encoding='utf-8')
    print('Gran 2e 8.0 E2E has deterministic startup, diagnostics, real PLAY swipes and retry waits')

if __name__=='__main__': main()
