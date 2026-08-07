# Physical device testing runbook

Everything in this file needs real hardware (real GPS, real OS power
management, real audio hardware, a human looking at a real screen) — none
of it can be meaningfully verified on an emulator, which is why it's
still open after everything that *could* be verified on one has been.
iOS is explicitly out of scope here; there's no iOS implementation yet
(see README's Follow-up work).

For each test: capture the pass/fail criteria described, plus
`adb logcat -s NavigationActivity:* MapboxNavFlutterV3Plugin:* Mapbox:* AndroidRuntime:*`
around the test window (screenshot or paste it) so a failure can actually
be diagnosed afterward rather than just re-reported as "didn't work."

## Setup

1. A physical Android device (Android 10+; Android 13+ specifically to
   exercise the `POST_NOTIFICATIONS` permission prompt in tests 4-5).
2. Your own Mapbox public access token (`pk....`) — run with
   `flutter run --dart-define=MAPBOX_ACCESS_TOKEN=pk.your-token` from
   `example/`.
3. `MAPBOX_DOWNLOADS_TOKEN` set in `~/.gradle/gradle.properties` (see
   README) to build at all.
4. Grant location permission when prompted; grant or deny notifications
   as instructed per-test below (both paths matter).

## 1. Real multi-turn drive

**Why an emulator couldn't cover this:** every test route so far has been
short and nearly straight-line (practical necessity for fast iteration).
The maneuver banner's turn-arrow/instruction formatting has now been seen
rendering *once*, for a single right turn, mid-reroute — never for a
left turn, a roundabout, consecutive turns, or a highway exit.

**Steps:**
1. Pick a real drive (or a long walk using `NavigationProfile.walking`)
   with at least 4-5 turns, including if possible a roundabout or a
   highway on/off-ramp.
2. Start navigation with `simulateRoute: false`, `simulateRoute` button's
   real-GPS counterpart in the example app.
3. Drive/walk the route to completion.

**Pass criteria:**
- Maneuver banner updates for every turn with a plausible instruction and
  arrow icon (not stuck on stale text, not blank).
- Trip progress bar's ETA/distance decreases monotonically (no jumps
  backward beyond what real GPS noise would explain).
- Voice instructions are audible at reasonable points before each turn
  (not silent, not firing after you've already turned).
- Arrival fires (`RESULT_ARRIVED`) within a reasonable distance of the
  actual destination — the default `arrivalDistanceMeters: 25` may need
  tuning based on what you observe (report the actual GPS drift you saw
  so the default can be revisited).

## 2. Marker + maneuver banner screenshots

Purely a documentation gap, not a suspected defect — capture what test 1
already exercises:
- A screenshot with the maneuver banner visibly showing a turn
  instruction.
- A screenshot with a `NavigationMarker` pin visible on the map alongside
  the route line.

Both can go in the README's Status section to close out the "not yet
independently caught in a screenshot" notes.

## 3. Audio interruption / ducking

**Steps:**
1. Start navigation with `voiceInstructionsEnabled: true` and music/a
   podcast playing in another app.
2. Let at least 2-3 voice instructions fire during playback.
3. Receive a phone call (or simulate one) during navigation; answer and
   end it.

**Pass criteria:**
- Voice instructions audibly duck (lower) other audio rather than being
  silent or fully stopping it, and other audio resumes normally after.
- A phone call doesn't crash navigation or leave voice instructions
  permanently silent afterward.

`MapboxVoiceInstructionsPlayer` is expected to handle audio focus
internally per Mapbox's own examples (nothing custom was built here) —
this test is verifying that assumption holds, not implementing anything
new.

## 4. Notification permission UX

**Steps:**
1. Fresh install (or clear app data) on Android 13+.
2. Start navigation. You should see two separate system permission
   prompts in sequence: location, then notifications (added this pass —
   see `example/lib/main.dart`).
3. Run it once **allowing** notifications, once **denying** them.

**Pass criteria:**
- Both prompts appear and are independently answerable (declining
  notifications must not block navigation from starting — it's
  non-fatal by design).
- When allowed: a trip-progress notification appears in the system tray
  once navigation starts (this is Mapbox's own built-in
  `NavigationNotificationService` — already merged into the manifest
  automatically, nothing custom was added). Check its content is
  reasonable (shows ETA/next maneuver, not blank or stale).
- When denied: navigation still works normally in the foreground. Note
  whether it's visibly worse in test 5 (background survival) as a result
  — that's expected on Android 14+, which can kill a location foreground
  service that has no visible notification.

## 5. Screen lock / backgrounding survival (the important one)

This is the biggest open question flagged in the README's Follow-up
work, and the one most likely to need an actual code change afterward.

**Steps, in order of increasing severity:**
1. Start navigation (real GPS, a route long enough to observe over a
   couple minutes).
2. **Screen lock only**: press the power button to lock the screen while
   still navigating. Wait 30s. Unlock.
3. **Home button**: press Home (app backgrounds but isn't force-stopped)
   while navigating. Wait 30s. Return to the app.
4. **Switch to another app**: open a different app and use it for a
   minute while navigation is presumably still running underneath. Return
   to the navigation app.

**For each step, check:**
- Did voice instructions keep firing while backgrounded/locked (audible
  even with the screen off)?
- Did the trip-progress notification (if notifications were allowed in
  test 4) keep updating while backgrounded?
- On returning to the app: is the maneuver banner/trip progress caught up
  with where you actually are, or stale/frozen from before you left?
- Did the process survive at all, or did Android kill it (check via
  `adb shell dumpsys activity processes | grep mapbox_navigation`)?

**If any of these fail** (navigation visibly pauses on lock/background):
the fix is almost certainly that `requireMapboxNavigation`'s
`onResumedObserver` ties observer attachment (and by extension, in the
current implementation, the trip session itself) to *this Activity's*
resumed state specifically. The documented Mapbox pattern for
background-survivable navigation is to attach `MapboxNavigationApp` to
`ProcessLifecycleOwner` instead of (or in addition to) the Activity. That
change needs exactly this kind of real-device verification to trust —
don't guess-fix it without re-running this test afterward, since a wrong
fix here risks introducing double route requests or a leaked trip
session on repeated attach/detach cycles.

## 6. Doze mode / extended background

**Steps:**
1. With navigation running and backgrounded (per test 5), leave the
   device completely idle (screen off, not touched) for 15+ minutes -
   long enough for Android's Doze mode to potentially engage.
2. Check in on it (unlock, return to the app).

**Pass criteria:** same as test 5, but after a longer, more aggressive
background period. If Doze visibly interrupts location updates or voice
instructions where a shorter background period didn't, that's useful
signal even if there's no immediate fix (Doze exemption for a
foreground-service-backed app is usually automatic, but worth
confirming rather than assuming).

## Reporting back

For anything that fails: which test, what you observed vs. expected, and
the logcat capture described at the top. That's enough to turn into a
targeted code fix + a re-run of just that test, rather than another full
pass through this whole runbook.
