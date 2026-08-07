# mapbox_navigation_flutter_v3

Full-screen turn-by-turn navigation for Flutter, built directly on
[Mapbox Navigation SDK v3](https://docs.mapbox.com/android/navigation/guides/)
(Android; iOS not yet implemented). Written to replace
`flutter_mapbox_navigation` (unmaintained, breaks on Android 14/15) with a
package this org actually maintains.

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
  Four real bugs were found and fixed only by actually running it (see
  Fixed section below) — the compiler alone did not catch any of them.
  **Turn-by-turn guidance and custom markers are now built and verified
  running** (not just compiled):
  - Trip progress bar (`MapboxTripProgressApi`/`MapboxTripProgressView`):
    confirmed rendering live distance/ETA/time-remaining data during an
    actual navigation session (screenshot showed `< 1 min` / `5 ft` /
    live clock updating in real time).
  - Voice instructions (`MapboxSpeechApi`/`MapboxVoiceInstructionsPlayer`/
    `VoiceInstructionsObserver`, gated by
    `options.voiceInstructionsEnabled`): confirmed the on-device TTS
    engine actually connects and cleanly disconnects across the session
    lifecycle (`TextToSpeech: Connected to TTS engine` /
    `Disconnected from TTS engine` in logcat), driven by a real
    `voice_instructions=true` directions request.
  - Maneuver banner (`MapboxManeuverApi`/`MapboxManeuverView`, gated by
    `options.bannerInstructionsEnabled`): wired into the same
    `RouteProgressObserver` tick as trip progress (which is confirmed
    live) and ran crash-free across 6+ full navigation sessions; not yet
    independently caught mid-render in a screenshot (short test routes +
    3x replay speed made the window hard to hit) — flagged as the one
    remaining gap in visual confirmation, not a known defect.
  - Custom markers (`PointAnnotationManager`, base64-encoded PNG icons
    passed from Dart via `NavigationMarker`): rendering crashed on first
    run (see Fixed section, bug 5) and has been crash-free across every
    run since the fix; not yet independently confirmed on-screen for the
    same screenshot-timing reason as the maneuver banner.
  Also untested: a physical device (only an emulator so far) and a real
  multi-minute drive with actual turn maneuvers along the way (test
  routes were short and mostly straight-line for practical testing
  reasons).
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
  case NavigationResult.error:
    // navigation failed to start or errored mid-route
}
```

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
- **`MarkerDecoder`** — validates and decodes marker payloads (base64 PNG
  icons) into `NavigationMarkerData`. A single malformed/oversized marker
  is dropped (logged) rather than failing the whole session; per-icon
  (`MAX_ICON_BYTES`) and total (`MAX_MARKERS`) caps guard against
  pathological input. The Base64/`BitmapFactory` step is injectable
  (`decodeIcon` parameter) specifically so the validation/cap/error-handling
  logic has real unit test coverage without needing Robolectric.
- **`PendingNavigationMarkers`** — same-process, same-pattern-as-
  `MapboxAccessToken` handoff for decoded marker bitmaps between the
  plugin and the Activity. Markers used to round-trip through the launch
  `Intent` as base64 JSON, which risked `TransactionTooLargeException`
  once real icons were involved (Android's Binder transaction buffer is
  ~1MB total) — since the Activity always launches in the same process,
  there's no need to serialize through the Intent/Binder at all.

## Follow-up work

- [ ] iOS implementation (`NavigationViewController` equivalent using
      Mapbox Navigation SDK v3 for iOS's standalone components).
- [ ] Off-route rerouting UI feedback (the SDK reroutes automatically;
      surfacing a "recalculating..." state to the user is not wired up).
- [ ] Test on a physical device (only tested on an emulator so far).
- [ ] Surface a specific failure reason for `NavigationResult.error`
      (currently logged natively via `Log.e`/`Log.w` but not threaded back
      to Dart — would need a small, deliberately-non-breaking addition to
      the result contract).
- [ ] Publish to pub.dev once the above is proven out.
