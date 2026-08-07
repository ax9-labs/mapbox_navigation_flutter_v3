# mapbox_navigation_flutter_v3

Full-screen turn-by-turn navigation for Flutter, built directly on
[Mapbox Navigation SDK v3](https://docs.mapbox.com/android/navigation/guides/)
(Android; iOS not yet implemented). Written to replace
`flutter_mapbox_navigation` (unmaintained, breaks on Android 14/15) with a
package this org actually maintains.

## Status (honest, as of first commit)

- **Dart API**: real, tested, stable — `initialize()`, `startNavigation()`,
  models. This won't need to change even as the native side evolves.
- **Android**: real implementation, **not yet compiled against the actual
  Mapbox SDK** — see [Blocker](#blocker-mapbox_downloads_token) below.
  Covers: route request, route line rendered on the map, a
  following camera, and arrival/cancel detection.
  **Not yet included**: turn-by-turn maneuver banner, trip progress bar,
  speed limit badge, voice instructions. Each is a real, separate Mapbox
  SDK component (`MapboxManeuverApi`/`View`, `MapboxTripProgressApi`/`View`,
  `MapboxVoiceInstructionsPlayer`) — same pattern as what's already wired
  up, just not done yet.
- **iOS**: not implemented. `startNavigation` currently returns a
  `NOT_IMPLEMENTED` error rather than hanging silently.

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

- [ ] Get a `MAPBOX_DOWNLOADS_TOKEN` and do a real first build; fix whatever
      the compiler finds (some class/method names here are grounded in
      Mapbox's current example source but haven't been compile-verified).
- [ ] iOS implementation (`NavigationViewController` equivalent using
      Mapbox Navigation SDK v3 for iOS's standalone components).
- [ ] Maneuver banner, trip progress, speed limit, voice instructions.
- [ ] Off-route rerouting UI feedback (the SDK reroutes automatically;
      surfacing a "recalculating..." state to the user is not wired up).
- [ ] Publish to pub.dev once the above is proven out on a real device.
