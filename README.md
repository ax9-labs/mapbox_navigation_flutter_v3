# mapbox_navigation_flutter_v3

Full-screen turn-by-turn navigation for Flutter, built directly on Mapbox
Navigation SDK v3 - Android's
[standalone components](https://docs.mapbox.com/android/navigation/guides/),
composed into a custom UI, and iOS's turnkey
[`NavigationViewController`](https://docs.mapbox.com/ios/navigation/guides/)
drop-in UI, themed to match. Written to replace `flutter_mapbox_navigation`
(unmaintained, breaks on Android 14/15) with a package this org actually
maintains.

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
- **iOS**: implemented on top of Mapbox's own turnkey
  `NavigationViewController` (unlike Android, iOS v3 still ships a drop-in
  UI, so there's no custom bottom sheet/maneuver-banner to build - see
  Architecture below). Verified so far, on the iOS Simulator:
  - Package resolves and compiles clean via Swift Package Manager
    (`flutter build ios`), pulling Mapbox Common/Core Maps/Maps/Navigation
    and their transitive deps.
  - App launches and reaches the example's "Ready" state without
    crashing.
  - **Found and fixed a real crash this way, not by inspection**: even
    though `initialize(accessToken:)` sets `MapboxOptions.accessToken` at
    Dart-call time, `MapboxNavigationCore`'s own `ApiConfiguration.default`
    reads `MBXAccessToken` from `Info.plist` independently (and caches the
    result in a `let` the first time anything touches it) - so on iOS,
    unlike Android, the token cannot be *purely* runtime-supplied; it also
    needs to be present in `Info.plist` by the time the app launches. See
    "iOS access token setup" below for how the example wires this without
    hardcoding a secret.
  - **A full simulated-route session, tap-through, on the Simulator, with
    a real Mapbox public token, is confirmed working end to end** (driven
    via `idb`, since `simctl` has no touch/tap subcommand of its own):
    tapping "Start navigation (simulated route)" → real
    `calculateRoutes(options:)` request returns a valid route →
    `NavigationViewController` presents with live map tiles, the route
    line, and a moving puck → maneuver banner and trip-progress bar update
    in real time as the simulated drive progresses ("West Dallas Avenue"
    200ft → "Date Palm Avenue" 0.5mi, "3 min / 0.8mi" → "2 min / 0.6mi") →
    Mapbox's own "You have arrived" screen appears → tapping "End
    Navigation" dismisses cleanly back to the Flutter app showing
    `Result: arrived`, round-tripped correctly through
    `NavigationViewControllerDelegate` → the method channel → the Dart
    `NavigationResult` enum. Not yet run: the real-GPS path (as opposed to
    simulated) and a physical device - see iOS follow-up work.

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

### iOS equivalent: `~/.netrc`

Mapbox Navigation SDK v3 for iOS ships via Swift Package Manager only (no
CocoaPods pod exists for it) - `Package.swift` declares the dependency, and
Flutter's SPM plugin support (3.24+, must be enabled once via
`flutter config --enable-swift-package-manager`) resolves it. The private
package registry needs the same `DOWNLOADS:READ`-scoped token as Android,
but via a different auth transport - `git`/`curl`-style HTTP Basic auth in
`~/.netrc`, not a Gradle property:

```
machine api.mapbox.com
login mapbox
password sk.your-secret-token
```

First resolution clones the full SDK repos (Mapbox Navigation, Mapbox Maps,
and their own transitive deps) via `git clone --mirror` - multiple hundred
MB, one-time cost, cached by SwiftPM afterwards
(`~/Library/Caches/org.swift.swiftpm`).

### iOS access token setup

Unlike Android (where `initialize(accessToken:)` alone is sufficient),
iOS's `MapboxNavigationCore` reads its access token from `Info.plist`'s
`MBXAccessToken` key independently of anything set at Dart-call time, and
caches whatever it finds (or doesn't) the first time that lookup happens -
see the Status section above for how this was found. `example/ios/Runner/
Info.plist` sets it to `$(MAPBOX_ACCESS_TOKEN)`, an Xcode build setting
resolved from the environment at build time (Xcode exposes arbitrary
inherited shell environment variables as build settings automatically), so
set the same value the Dart side uses before building/running:

```bash
export MAPBOX_ACCESS_TOKEN=pk.your-public-token
flutter run --dart-define=MAPBOX_ACCESS_TOKEN=$MAPBOX_ACCESS_TOKEN
```

Consumers embedding this plugin in their own app should do the same in
their `ios/Runner/Info.plist` (or hardcode a real public token there
directly - Mapbox public tokens are meant to ship inside client apps,
unlike the `DOWNLOADS:READ` secret token above).

The example's iOS deployment target is 14.0 (bumped up from Flutter's
default 13.0), the minimum the Mapbox iOS package requires - a consuming
app's own `ios/Podfile`/Xcode project needs the same minimum.

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

## Architecture (iOS)

iOS v3 (unlike Android v3) still ships a turnkey drop-in
`NavigationViewController` - maneuver banner, trip progress, voice, camera,
and arrival detection are all built in. This makes the iOS side much
smaller than Android's: there's no custom bottom sheet, maneuver-banner
view, or camera state machine to build - `NavigationCoordinator` is mostly
configuration and glue, not UI construction:

- **`NavigationCoordinator`** — the iOS counterpart to `NavigationActivity`,
  but scoped to configuration rather than UI: resolves a fresh origin
  (`OriginResolver`, the same fresh-fix-with-timeout-fallback pattern as
  Android's `resolveOrigin()`, for the same reason - a location cache with
  no freshness bound risks silently using a stale fix as the route origin),
  requests a route via `RoutingProvider.calculateRoutes(options:)`
  (Swift's `async`/`await`, no completion-handler boilerplate), and
  presents `NavigationViewController` with a themed style + congestion
  config + markers. `@MainActor`-isolated throughout, since
  `MapboxNavigationProvider`/`RouteVoiceController`/`NavigationMapView`
  all are.
- **`NavigationTheme`** — maps `TurnByTurnTheme` onto Mapbox's own
  `DayStyle`/`NightStyle` via `UIAppearance` proxy styling (the same
  mechanism Mapbox's own examples use) rather than a custom view
  hierarchy, since there's no custom view hierarchy here to theme -
  congestion colors go through `NavigationMapView.congestionConfiguration`
  instead, since that's not a `UIAppearance` property.
- **`TurnByTurnTheme`/`TurnByTurnUiOptions`/`NavigationStartOptions`/
  `MarkerDecoder`** — direct Swift ports of the Android Kotlin decoders,
  same field-for-field behavior. One simplification: since iOS never
  leaves the host app's process (no separate "Activity" launched via
  `Intent` the way Android does), there's no JSON round-trip layer -
  everything decodes straight from the `[String: Any]`/`[[String: Any]]`
  dictionaries Flutter's method channel already provides, and marker icons
  arrive as `FlutterStandardTypedData` (no base64, same as Android).

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
iOS currently implements this same set except `LOCATION_PROVIDER_DISABLED`
and `ROUTE_REQUEST_CANCELED` (Mapbox's iOS routing API doesn't expose a
distinct disabled-provider signal or cancellation as separate cases the
way Android's does) - a route request failure surfaces as
`ROUTE_REQUEST_FAILED` on both platforms either way.

## Follow-up work

- [x] iOS implementation (`NavigationCoordinator` presenting Mapbox's
      turnkey `NavigationViewController`, themed to match) - see
      Architecture (iOS) above.
- [x] iOS tap-through verification with a real Mapbox public token
      (simulated-route path): route request → drop-in UI renders live map/
      route/puck → maneuver banner and trip progress update in real time →
      arrival screen → clean dismissal back to `Result: arrived`. Driven
      via `idb` (`brew install idb-companion` + `pip3 install --user
      fb-idb`), since `simctl` has no tap subcommand of its own.
- [ ] Same tap-through check on the real-GPS path (`Start navigation (real
      GPS)`), not just the simulated-route path above.
- [ ] iOS physical-device verification, mirroring the Android runbook -
      background/lock-screen survival (does `NavigationViewController`'s
      session survive backgrounding the same way Android's foreground
      trip session does?), a real multi-turn drive, audio interruption,
      voice-guidance background-audio-mode behavior.
- [ ] Migrate the example's Xcode project fully to Swift Package Manager
      (`pod deintegrate` + remove `Podfile`) - it currently builds fine
      with SPM-for-our-plugin alongside legacy CocoaPods integration for
      everything else, but Flutter's own tooling flags this as a
      recommended cleanup for build time.
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
- [ ] Add an iOS job to CI (`flutter build ios --simulator --no-codesign`)
      alongside the existing Android job - needs a repo secret with a
      `DOWNLOADS:READ` token wired into `~/.netrc` in the workflow (same
      token as `MAPBOX_DOWNLOADS_TOKEN`, different transport - see "iOS
      equivalent: `~/.netrc`" above), which requires repo-settings access
      this session didn't have.
- [ ] Publish to pub.dev once the above is proven out.
