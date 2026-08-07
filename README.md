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
  **Not yet built**: maneuver banner, trip progress bar, speed limit
  badge, voice instructions, and off-route rerouting UI feedback — each
  is a real, separate Mapbox SDK component (`MapboxManeuverApi`/`View`,
  `MapboxTripProgressApi`/`View`, `MapboxVoiceInstructionsPlayer`), same
  pattern as what's already working, just not done. Also untested: a
  physical device (only an emulator so far) and a real multi-minute drive
  with actual maneuvers along the way (both test routes were short,
  mostly-straight-line for practical testing reasons).
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
   lookup, prepended to the coordinates list.
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
  ),
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

## Follow-up work

- [ ] Verify arrival detection (`RESULT_ARRIVED`) actually fires — pick a
      short simulated route (a couple miles) and let it run to completion.
- [ ] Test the real-GPS path, not just simulated.
- [ ] iOS implementation (`NavigationViewController` equivalent using
      Mapbox Navigation SDK v3 for iOS's standalone components).
- [ ] Maneuver banner, trip progress, speed limit, voice instructions.
- [ ] Off-route rerouting UI feedback (the SDK reroutes automatically;
      surfacing a "recalculating..." state to the user is not wired up).
- [ ] Test on a physical device (only tested on an emulator so far).
- [ ] Publish to pub.dev once the above is proven out.
