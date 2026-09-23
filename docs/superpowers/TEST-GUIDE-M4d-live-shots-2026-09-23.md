# Bench Test Guide — M4d live shots (golf sim)

Written 2026-09-23. This is the manual on-device checklist for PR
`m4d-live-shots` (plan: `docs/superpowers/plans/2026-09-23-m4d-live-shots.md`,
handover: `docs/superpowers/HANDOVER-M4d-live-shots-2026-09-23.md`).
**All six checks must pass before the PR merge.** ~10 minutes with the
monitor, net/bucket, and tablet.

## Setup (already done 2026-09-23)

- App installed on the Lenovo TB373FU and launched (`MainActivity`).
- If the install ever fails later with INSTALL_FAILED_UPDATE_INCOMPATIBLE:
  `adb uninstall com.hpsmiles.golfsim`, reinstall.
- Pre-flight (from the M4c doc §4.10): Awesome Golf authorization must be
  current (Rapsodo app → Play → Simulation → 3rd Party Apps → Authenticate
  Now, valid 24 h). The MLM2PRO must NOT be connected in the Rapsodo app,
  and it must be awake, or it won't advertise.

## Checklist

Tick each box as it passes; note failures exactly as observed (which check,
what happened, rough timing). That is all the post-testing handover needs.

### 1. Connect (regression — M4b/M4c behaviour unchanged)
- [ ] Single tap CONNECT → AUTH/token/config fine → **green ARMED LED**
      within a few seconds. No repeated taps needed.

### 2. Demo fold intact (refactor didn't break the demo pipeline)
- [ ] Toggle DEMO ON → tap FIRE several times → shots append to the list,
      tracer animates on each, metrics change per shot.

### 3. Live shots render — the new M4d core path
- [ ] Toggle DEMO OFF (live mode), hit a normal shot at the bucket/net.
- [ ] Real shot appears in the shot list within ~2 s; tracer draws; metrics
      render in both canvases (top-down + POV).
- [ ] Numbers plausible for the club (8i reference from M4c: ball speed
      98.9–107.0 mph, VLA 16–23°, spin 5.9k–8.2k rpm, smash 1.21–1.28).

### 4. Misread pill — appears ONCE, dismisses
- [ ] Hit one deliberate duff/duff-heavy shot.
- [ ] Expect exactly ONE increment of the pill: **"no read (1)"** —
      NOT two. (One mishit emits both an EVENTS `05 00` and an all-zero
      MEASUREMENT ~200 ms apart; the 500 ms coalescing must collapse them.)
- [ ] Tap "dismiss" → pill disappears.

### 5. Auto-ARM cadence survives a misread
- [ ] Immediately after the duff in check 4, hit a clean shot → it appends
      normally (auto-ARM re-fires ~500 ms after READY, per M4b/M4c).

### 6. Capture/export untouched by the refactor
- [ ] Settings → DEBUG capture: toggle CAPTURE ON (count climbs with
      shots), hit a shot or two, EXPORT works (share intent fires).

## Failure notes (fill in)

```
check #: 
seen:
```
