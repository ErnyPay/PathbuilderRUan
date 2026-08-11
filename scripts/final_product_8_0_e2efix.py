#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TEST=ROOT/'ci/final_product_e2e.sh'


def main():
    s=TEST.read_text(encoding='utf-8')
    if 'tap_tab(){' not in s:
        marker='tap_desc(){ local xy; xy="$(coord_desc "$1")"; log "tap desc $1 @ $xy"; adb shell input tap $xy; sleep 1; }\n'
        if marker not in s: raise SystemExit('E2E tap_desc helper not found')
        helper=r'''tap_tab(){
  local target="$1" desc="play-tab-$1" xy i
  # Return the horizontal PLAY strip to its left edge, then swipe across it
  # exactly as a user would until the requested tab becomes visible.
  for i in $(seq 1 8); do adb shell input swipe 160 330 930 330 120 >/dev/null; done
  sleep 1
  for i in $(seq 1 10); do
    if xy="$(coord_desc "$desc" 2>/dev/null)"; then
      log "tap tab $target @ $xy"
      adb shell input tap $xy
      sleep 1
      return 0
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
        s=s.replace(marker,marker+'\n'+helper+'\n')
    for target in ['spells','gear','effects','pets','attacks','defenses','skills','feats']:
        s=s.replace('tap_desc play-tab-'+target,'tap_tab '+target)
    TEST.write_text(s,encoding='utf-8')
    print('Gran 2e 8.0 E2E now swipes the horizontal PLAY tab strip')

if __name__=='__main__': main()
