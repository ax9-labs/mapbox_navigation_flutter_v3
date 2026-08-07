# mapbox_navigation_flutter_v3

Full-screen turn-by-turn navigation for Flutter, built directly on
[Mapbox Navigation SDK v3](https://docs.mapbox.com/android/navigation/guides/)
(Android; iOS not yet implemented). Written to replace
`flutter_mapbox_navigation` (unmaintained, breaks on Android 14/15) with a
package this org actually maintains.

[![CI](https://github.com/ax9-labs/mapbox_navigation_flutter_v3/actions/workflows/ci.yml/badge.svg)](https://github.com/ax9-labs/mapbox_navigation_flutter_v3/actions/workflows/ci.yml)

CI runs `flutter analyze`/`flutter test` unconditionally, plus the Android
native unit tests (`testDebugUnitTest`) — the Android job needs a repo
secret `MAPBOX_DOWNLOADS_TOKEN` (Settings → Secrets and variables →
Actions) with the `DOWNLOADS:READ` scope described below, or it fails at
Gradle dependency resolution rather than at the tests themselves.

## Status (honest, as of the first real device/emulator test)

- **Dart API**: real, tested, stable — `initialize()`, `startNavigation()`,
  models. This won't need to change even as the native side evolves.
- **Android**: **the full happy-path matrix is verified working on a real
  emulator (Android 16 / API 36, 16KB page size image)**, via `example/`'s
  two buttons:
  - Route request (device location → destination) → Mapbox returns a
    valid route → route line rendered on the map.
  - Simulated drive (`ReplayProgressObserver`-driven): puck moves,
    following camera tracks it at street-level zoom, **arrival correctly
    fires `RESULT_ARRIVED`** (confirmed with a short ~500m test route).
  - Real-GPS-driven trip session (`replay enabled: false`, fed via
    `adb emu geo fix` standing in for real GPS): same arrival detection
    confirmed working on this path too, independently of the replay path.
  - Cancel (back-press mid-navigation) → `RESULT_CANCELLED`, confirmed.
  - Malformed input (missing origin) → fails closed with `RESULT_ERROR`
    instead of crashing, confirmed.
  Nine real bugs were found and fixed only by actually running it (see
  Fixed section below) — the compiler alone did not catch any of them.
  **Turn-by-turn guidance, custom markers, automatic reroute feedback, and
  a redesigned production-style UI are built and verified running** (not
  just compiled) — see `TURN_BY_TURN_UI_REDESIGN.md` for the full
  deliverables report on the UI redesign specifically. Highlights:
  - Primary maneuver banner + "Then ↱ ..." next-maneuver preview + a
    `BottomSheetBehavior`-driven trip sheet (stop button, large remaining
    duration, "distance • arrival time", a context-sensitive action
    button, and a detailed maneuver row) - **all visually confirmed
    rendering together in one real navigation session**, not just each
    piece individually (a right-turn icon + "North 32nd Lane" + "200 ft"
    in the top banner, "8 min" / "2.2 mi • 4:19 pm" in the trip sheet, all
    populated from live route progress).
  - Voice instructions (`MapboxSpeechApi`/`MapboxVoiceInstructionsPlayer`/
    `VoiceInstructionsObserver`, gated by
    `options.voiceInstructionsEnabled`): confirmed the on-device TTS
    engine actually connects and cleanly disconnects across the session
    lifecycle, driven by a real `voice_instructions=true` directions
    request.
  - Custom markers (`PointAnnotationManager`, raw PNG bytes passed from
    Dart via `NavigationMarker`): visually confirmed rendering on the map
    (a red pin) alongside the route line; crash-free across every run
    since the ARGB_8888 fix (see Fixed section).
  - Reroute feedback (`RerouteController.RerouteStateObserver`, gated on
    `RerouteState.FetchingRoute`): triggered a real automatic reroute by
    deviating off-route on the emulator (Mapbox's own
    `RerouteController` requested and applied a new route within ~1s,
    confirmed via `reason=deviation` in the directions request log and
    the route line updating) — crash-free.
  - Recenter control, camera FOLLOWING/OVERVIEW toggle, arrival panel
    (checkmark + brief pause before the session ends), and
    permission/location error surfacing (`LOCATION_PERMISSION_DENIED` with
    a specific message) — all visually confirmed on-device.
  - Session-ending via the hardware back button confirmed working
    (`RESULT_CANCELLED`) on the redesigned UI; the on-screen stop button
    exercises the identical code path but wasn't independently
    tap-confirmed this session (emulator touch-input reliability degraded
    late in a long testing session) — flagged for physical-device
    verification, not a known defect.
  Also untested: a physical device (only an emulator so far) and a real
  multi-minute drive with actual turn maneuvers along the way (test
  routes were short and mostly straight-line for practical testing
  reasons) — see `PHYSICAL_DEVICE_TESTING.md` for the runbook covering
  what's left.
- **iOS**: not implemented. `startNavigation` currently returns a
  `NOT_IMPLEMENTED` error rather than hanging silently.

### Fixed by actually running it (not caught by the compiler)

1. **`IllegalStateException: MapboxNavigation cannot be null`** — the
   `by requireMapboxNavigation(...)` delegate was force-triggered
   synchronously at the tail of `onCreate()`, before the Activity's
   lifecycle had finished reaching `CREATED`. Fixed by moving first access
   to `onStart()`.
2. **`Not enough input coordinates given; minimum number of coordinates is
   2`** — the route request was built straight from the Dart-supplied
   `waypoints` (destination only), never prepending the device's current
   location as the origin, even though that's exactly what the Dart API
   promises. Fixed with a one-shot `LocationManager.getLastKnownLocation`
   lookup, prepended to the coordinates list. **Superseded (see #7 below):**
   this cache-only approach itself turned out to be unreliable enough to
   bite us during later testing.
3. **Simulated route never moved** — `startReplayTripSession()` alone only
   configures replay *mode*; it doesn't feed the replayer any events.
   Nothing happened until a `ReplayProgressObserver` was registered *and*
   seeded with one `ReplayRouteMapper.mapToUpdateLocation` event at the
   origin — from there it self-sustains off route-progress ticks.
4. **Repo scoping** (`example/android/build.gradle.kts`, not the Kotlin
   code): the Mapbox Maven repo was originally declared only in the
   plugin's own `build.gradle.kts`. A module's `allprojects {}` only
   configures that module (it has no subprojects of its own) — it doesn't
   propagate to `:app`, which is what actually resolves the final
   dependency graph. **Every app consuming this plugin needs the same
   Mapbox repo block in its own root `build.gradle.kts`** — see
   `example/android/build.gradle.kts` for the exact block to copy.
5. **`IllegalArgumentException: Only ARGB_8888 bitmap config is
   supported!`** — crashed on the very first custom-marker run.
   `BitmapFactory.decodeByteArray(bytes, 0, bytes.size)` (no options) does
   not reliably return an `ARGB_8888` bitmap — it depends on the source
   PNG's color type, and Mapbox's `PointAnnotationManager` image path
   (`ExtensionUtils.toMapboxImage`) hard-requires that config. Fixed by
   passing `BitmapFactory.Options().apply { inPreferredConfig =
   Bitmap.Config.ARGB_8888 }` explicitly and converting via `.copy(...)`
   if the decoder still returns something else.
6. **`androidx.constraintlayout` compile error** —
   `MapboxManeuverView extends ConstraintLayout`, but `ui-components`
   only brings that in as a transitive `implementation` dependency, which
   Gradle doesn't expose on this module's own compile classpath (needed
   because the Activity references the view's Kotlin type directly, not
   just via XML inflation). Fixed by adding
   `androidx.constraintlayout:constraintlayout:2.1.4` directly to
   `android/build.gradle.kts`.
7. **Route origin silently stale** — not a crash, but a real correctness
   bug found the hard way: `lastKnownLocationPoint()` (bug #2's fix) had no
   freshness bound at all, and during later testing it returned a location
   cached from an unrelated earlier test session, silently producing a
   trivially-already-arrived "route." Fixed by resolving the origin with a
   single fresh `LocationManager.requestSingleUpdate()` first, falling back
   to the last-known-location cache only if a fresh fix doesn't arrive
   within 5s (`NavigationActivity.resolveOrigin`).
8. **Marker Intent size risk** — not something that crashed in our own
   testing (only one small test marker was ever used), but identified in a
   code review as a real latent bug: base64 marker icons were round-tripped
   through the launch `Intent` as JSON, which risks
   `TransactionTooLargeException` (Android's Binder transaction buffer is
   ~1MB total) with more than a handful of real icons — exactly the
   scenario custom markers exist for. Fixed by moving markers to a
   same-process in-memory handoff (`PendingNavigationMarkers`) instead;
   see the Architecture section below.
9. **Cancel (back-press) silently became `RESULT_ERROR` instead of
   `RESULT_CANCELLED`** — found by re-running the exact scenario the
   original bug list called "confirmed" working, after unrelated changes
   elsewhere in this file. `override fun onBackPressed()` is never invoked
   at all on Android builds with the predictive-back gesture enabled —
   back navigation instead routes through `OnBackInvokedCallback`, so the
   Activity finished via the system's default behavior with no
   `setResult()` call ever made, which the plugin then defaulted to an
   error. Fixed with `onBackPressedDispatcher.addCallback(this) { ... }`,
   the AndroidX-recommended replacement that works under both gesture and
   legacy back navigation. A reminder that "confirmed working" claims in
   this file are only as good as the last time that exact path was
   actually re-run — nothing here regression-tests Activity lifecycle
   behavior (see Follow-up work).

### Why "composed" instead of a drop-in screen

Mapbox's own current example repo
([mapbox-navigation-android-examples](https://github.com/mapbox/mapbox-navigation-android-examples),
`main` branch) no longer demonstrates a turnkey Drop-In UI screen — only
these lower-level "standalone" components, composed by the app. That's
what this plugin does. (The `main-v2` branch of that repo has the old
Drop-In UI examples, for reference, but v2 is the version Mapbox is moving
away from.)

## Blocker: `MAPBOX_DOWNLOADS_TOKEN`

The Mapbox Navigation SDK ships from a private Maven repo gated behind a
**secret** downloads token — separate from the public access token you pass
to `initialize()` at runtime. Without it, Gradle can't even resolve the
dependency, so the Android module hasn't been compiled yet.

1. Get a token with the `DOWNLOADS:READ` scope from
   [account.mapbox.com/access-tokens](https://account.mapbox.com/access-tokens/).
2. Add it to `~/.gradle/gradle.properties` (**never commit this**):
   ```properties
   MAPBOX_DOWNLOADS_TOKEN=sk.your-secret-token
   ```
3. Build the `example/` app.

## Usage

```dart
final nav = MapboxNavigationFlutterV3();
await nav.initialize(accessToken: 'pk.your-public-token');

try {
  final result = await nav.startNavigation(
    waypoints: [
      NavigationWaypoint(latitude: 26.2034, longitude: -98.2300, name: 'Safe Zone'),
    ],
    options: const NavigationOptions(
      profile: NavigationProfile.drivingTraffic,
      // Both optional - defaults (25m / 3x) suit driving; tighten
      // arrivalDistanceMeters for NavigationProfile.walking, or raise it for
      // open-highway driving where GPS drift is larger relative to road width.
      arrivalDistanceMeters: 25,
      simulateSpeedMultiplier: 3,
    ),
    markers: [
      NavigationMarker(
        id: 'incident-1',
        latitude: 26.21,
        longitude: -98.23,
        icon: incidentIconPngBytes, // must be non-empty; ids must be unique
      ),
    ],
  );

  switch (result) {
    case NavigationResult.arrived:
      // user reached the destination
    case NavigationResult.cancelled:
      // user backed out
  }
} on NavigationException catch (e) {
  // Any failure - couldn't start (missing permission, bad token) or
  // failed mid-route (no route found, location became unavailable) -
  // arrives here. e.code is a stable identifier (e.g.
  // "LOCATION_PERMISSION_DENIED", "ROUTE_REQUEST_FAILED", "NO_WAYPOINTS");
  // e.message is a human-readable detail for logging.
}
```

### Theming and UI configuration

`NavigationOptions.theme` and `.uiOptions` customize the turn-by-turn
screen's colors and which pieces of it show - see
`TURN_BY_TURN_UI_REDESIGN.md` for the full redesign write-up (what's built,
what's deliberately deferred, and why).

```dart
options: NavigationOptions(
  theme: const TurnByTurnTheme(
    primaryInstructionColor: 0xFF0057FF, // ARGB int, e.g. myColor.value
    cornerRadius: 16,
  ),
  uiOptions: const TurnByTurnUiOptions(
    confirmBeforeExitNavigation: true,
    showTrafficSignals: false, // default - see note below
  ),
),
```

Every `TurnByTurnTheme` field is optional (`null` = built-in default,
which respects light/dark mode); every `TurnByTurnUiOptions` flag defaults
to the full experience. `showTrafficSignals` is a reserved extension point,
not a working feature yet — the Mapbox Navigation SDK doesn't currently
expose reliable traffic-signal/intersection data for this plugin to render
from, so turning it on is a no-op rather than fabricated markers.

## Architecture (Android)

`NavigationActivity` composes the map/camera/turn-by-turn UI and owns the
Mapbox Navigation SDK lifecycle, but the pure-logic pieces around it are
deliberately factored out so they're unit-testable on the plain JVM
(`./gradlew :mapbox_navigation_flutter_v3:testDebugUnitTest`, no
Robolectric/emulator needed):

- **`NavigationIntentCodec`** — encodes/decodes waypoints and options
  to/from the JSON strings carried as launch-`Intent` extras. Decoding is
  defensive: malformed JSON degrades to an empty list / default options
  (logged) rather than crashing `onCreate()`.
- **`MarkerDecoder`** — validates and decodes marker payloads (raw PNG
  bytes - see below) into `NavigationMarkerData`. A single
  malformed/oversized marker is dropped (logged) rather than failing the
  whole session; per-icon (`MAX_ICON_BYTES`) and total (`MAX_MARKERS`)
  caps guard against pathological input. The `BitmapFactory` decode step
  is injectable (`decodeIcon` parameter) specifically so the
  validation/cap/error-handling logic has real unit test coverage without
  needing Robolectric.
- **`PendingNavigationMarkers`** — same-process, same-pattern-as-
  `MapboxAccessToken` handoff for decoded marker bitmaps between the
  plugin and the Activity. Markers used to round-trip through the launch
  `Intent` as base64 JSON, which risked `TransactionTooLargeException`
  once real icons were involved (Android's Binder transaction buffer is
  ~1MB total) — since the Activity always launches in the same process,
  there's no need to serialize through the Intent/Binder at all.

### Marker icon transport (no base64)

`NavigationMarker.icon` crosses the platform channel as a plain
`Uint8List`/`ByteArray`, not a base64 string. Flutter's
`StandardMessageCodec` supports `Uint8List` as a first-class binary type
on both sides of the channel — base64 would only add ~33% size overhead
and an encode/decode step for no benefit. (This is unrelated to the
Intent-size fix above, which was about the *plugin → Activity* leg, not
the *Dart → plugin* leg.)

### Error handling

`startNavigation()` only returns `NavigationResult.arrived` or
`NavigationResult.cancelled` — there is no `NavigationResult.error`.
Every failure, whether navigation couldn't start at all (missing
permission, no access token) or failed partway through (no route found,
location became unavailable, canceled), throws `NavigationException(code,
message)` instead. `code` is a stable, switchable identifier:
`NOT_INITIALIZED`, `NO_WAYPOINTS`, `NO_ACTIVITY`, `ALREADY_NAVIGATING`,
`LOCATION_PERMISSION_DENIED`, `LOCATION_PROVIDER_DISABLED`,
`LOCATION_UNAVAILABLE`, `ROUTE_REQUEST_FAILED`, `ROUTE_REQUEST_CANCELED`.

## Follow-up work

- [ ] iOS implementation (`NavigationViewController` equivalent using
      Mapbox Navigation SDK v3 for iOS's standalone components).
- [ ] Physical-device verification — see `PHYSICAL_DEVICE_TESTING.md` for
      the runbook (background/lock-screen survival, a real multi-turn
      drive, audio interruption, notification permission UX, marker/
      maneuver-banner screenshots).
- [ ] `NavigationActivity` itself (the lifecycle wiring - observer
      registration, route/trip-session sequencing) has zero automated
      test coverage; only the pure logic extracted into
      `NavigationIntentCodec`/`MarkerDecoder` is unit-tested. The three
      original runtime bugs (lifecycle timing, replay seeding, bitmap
      config) and the back-press regression all lived in exactly this
      untested wiring. Robolectric or instrumented (Espresso) tests would
      close this gap; neither is set up.
- [ ] Background trip-session continuity beyond what Mapbox's SDK
      provides automatically (a foreground-service trip notification —
      manifest permissions/service already merge in from the SDK, no
      action needed there) has not been verified end to end: does
      `requireMapboxNavigation`'s Activity-scoped attach/detach
      correctly survive the Activity pausing (screen lock, backgrounding),
      or does the whole trip session (not just this plugin's own UI
      observers) tear down with it? This needs a real device to answer
      honestly — see the runbook.
- [ ] Publish to pub.dev once the above is proven out.
