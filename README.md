# mapbox_navigation_flutter_v3

[![CI](https://github.com/ax9-labs/mapbox_navigation_flutter_v3/actions/workflows/ci.yml/badge.svg)](https://github.com/ax9-labs/mapbox_navigation_flutter_v3/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Full-screen, turn-by-turn driving/walking/cycling navigation for Flutter,
built directly on **Mapbox Navigation SDK v3** — Android's standalone
components composed into a themed custom UI, and iOS's turnkey
[`NavigationViewController`](https://docs.mapbox.com/ios/navigation/guides/)
drop-in UI, themed to match. One Dart API, two real native implementations
underneath — not a thin wrapper around a webview or a single-platform stub.

Written to replace [`flutter_mapbox_navigation`](https://pub.dev/packages/flutter_mapbox_navigation)
(unmaintained, breaks on Android 14/15) with a package this org actually
maintains and has run end to end on both platforms.

## Features

- Turn-by-turn navigation UI: maneuver banner, trip progress, route line,
  voice guidance, automatic reroute on deviation, arrival detection.
- Custom map markers (raw PNG bytes, no base64 overhead).
- Theming: override colors/corner radius/elevation to match your app, or
  hide individual UI pieces (traffic, recenter button, etc).
- Simulated or real-GPS-driven trips (simulated is useful for demos/testing
  without physically driving anywhere).
- One stable Dart API (`initialize()` / `startNavigation()`) across both
  platforms — the same call site works whether you're running on Android
  or iOS.

## Platform support

| | Android | iOS |
|---|---|---|
| Minimum version | API 24 | 14.0 |
| Underlying UI | Composed from Mapbox's standalone components | Mapbox's own drop-in `NavigationViewController` |
| Status | Verified on emulator (see [status](docs/DEVELOPMENT_LOG.md)) | Verified on Simulator (see [status](docs/DEVELOPMENT_LOG.md)) |

Physical-device verification on both platforms is still outstanding —
see [Known limitations](#known-limitations) before shipping to production.

## Getting started

### 1. Add the dependency

Not yet published to pub.dev (pending physical-device verification — see
[Known limitations](#known-limitations)). Until then, depend on it
directly from GitHub:

```yaml
dependencies:
  mapbox_navigation_flutter_v3:
    git:
      url: https://github.com/ax9-labs/mapbox_navigation_flutter_v3.git
```

### 2. Get your Mapbox tokens

This plugin needs **two different Mapbox tokens** that are easy to
confuse — get both up front:

| Token | Scope | Used for | Where it goes |
|---|---|---|---|
| **Public access token** (`pk.…`) | default/public | Runtime map/routing requests | Passed to `initialize()` in your Dart code |
| **Secret downloads token** (`sk.…`) | `DOWNLOADS:READ` | Resolving the Mapbox Navigation SDK itself at build time | Your machine's `~/.gradle/gradle.properties` (Android) and `~/.netrc` (iOS) — **never committed, never shipped in the app** |

Create both at [account.mapbox.com/access-tokens](https://account.mapbox.com/access-tokens/).
The public token is meant to ship inside client apps (that's how Mapbox
public tokens work); the secret token must never leave your machine/CI.

### 3. Android setup

1. Add the secret downloads token to `~/.gradle/gradle.properties` on
   every machine (and CI runner) that builds this app:
   ```properties
   MAPBOX_DOWNLOADS_TOKEN=sk.your-secret-token
   ```
2. Add Mapbox's private Maven repo to your app's **root**
   `android/build.gradle.kts` (this has to live in your app's root file,
   not just the plugin's — a module's `allprojects {}` doesn't propagate
   to `:app`):
   ```kotlin
   val mapboxDownloadsToken: String =
       (project.findProperty("MAPBOX_DOWNLOADS_TOKEN") as String?)
           ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN")
           ?: ""

   allprojects {
       repositories {
           google()
           mavenCentral()
           maven {
               url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
               authentication { create<BasicAuthentication>("basic") }
               credentials {
                   username = "mapbox"
                   password = mapboxDownloadsToken
               }
           }
       }
   }
   ```
3. Set `minSdk = 24` (or higher) in your app's `android/app/build.gradle.kts`.
4. Add location permissions to `android/app/src/main/AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
   <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
   <uses-permission android:name="android.permission.INTERNET" />
   ```

### 4. iOS setup

1. Enable Flutter's Swift Package Manager plugin support (one-time, per
   machine/CI runner) — required because Mapbox's iOS Navigation SDK v3
   ships via SPM only, no CocoaPods pod exists for it:
   ```bash
   flutter config --enable-swift-package-manager
   ```
2. Add the secret downloads token to `~/.netrc` (same token as Android's
   `MAPBOX_DOWNLOADS_TOKEN`, different transport — SPM resolves the
   private package registry via HTTP Basic auth):
   ```
   machine api.mapbox.com
   login mapbox
   password sk.your-secret-token
   ```
   First resolution clones the SDK's repos via `git clone --mirror`
   (several hundred MB) — a one-time cost, cached by SwiftPM afterwards.
3. Set the iOS deployment target to `14.0` (or higher) — in
   `ios/Podfile` and in your Xcode project's build settings.
4. Add to `ios/Runner/Info.plist`:
   - Location usage description(s):
     ```xml
     <key>NSLocationWhenInUseUsageDescription</key>
     <string>Used to show turn-by-turn navigation.</string>
     ```
   - Background modes, if you want voice guidance / location updates to
     keep working while backgrounded:
     ```xml
     <key>UIBackgroundModes</key>
     <array>
       <string>audio</string>
       <string>location</string>
     </array>
     ```
   - **Your public access token**, since iOS's Mapbox SDK reads it from
     `Info.plist` independently of anything passed to `initialize()` at
     runtime (see [why](docs/DEVELOPMENT_LOG.md) if you're curious):
     ```xml
     <key>MBXAccessToken</key>
     <string>pk.your-public-token</string>
     ```
     Or, to avoid hardcoding it, use a build-setting placeholder and set
     the matching environment variable at build time (see `example/` for
     the full pattern):
     ```xml
     <key>MBXAccessToken</key>
     <string>$(MAPBOX_ACCESS_TOKEN)</string>
     ```

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

Both platforms require the location permission to be granted *before*
calling `startNavigation()` — this plugin doesn't request it for you (use
a package like [`permission_handler`](https://pub.dev/packages/permission_handler),
as the `example/` app does).

### Theming and UI configuration

`NavigationOptions.theme` and `.uiOptions` customize the turn-by-turn
screen's colors and which pieces of it show:

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
from, so turning it on is a no-op rather than fabricated markers. See
`TURN_BY_TURN_UI_REDESIGN.md` for the full theming reference (every field,
what it maps to on each platform).

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
way Android's does) — a route request failure surfaces as
`ROUTE_REQUEST_FAILED` on both platforms either way.

## Example app

`example/` is a full runnable app with both platforms wired up — the
fastest way to see this plugin working end to end, and the reference for
every setup step above. Run it with your own public token:

```bash
cd example
export MAPBOX_ACCESS_TOKEN=pk.your-public-token
flutter run --dart-define=MAPBOX_ACCESS_TOKEN=$MAPBOX_ACCESS_TOKEN
```

(The `export` matters for iOS specifically — see the `Info.plist` step
above — Android only needs the `--dart-define`.)

## Known limitations

- **Physical-device testing is still outstanding on both platforms** —
  everything above has been verified on an Android emulator and the iOS
  Simulator, including a live tap-through session with a real Mapbox
  token, but not yet on real hardware. Background/lock-screen survival in
  particular (does the trip session survive the OS backgrounding your
  app?) can only be answered honestly on a real device.
- `showTrafficSignals` is a reserved flag, not a working feature (see
  above).
- iOS's real-GPS navigation path (as opposed to simulated) hasn't been
  tap-through tested yet, only the simulated-route path has.

See `docs/DEVELOPMENT_LOG.md` for the full, detailed status/testing log
and the complete roadmap, and `PHYSICAL_DEVICE_TESTING.md` for the
physical-device runbook.

## Contributing

Issues and PRs welcome at
[github.com/ax9-labs/mapbox_navigation_flutter_v3](https://github.com/ax9-labs/mapbox_navigation_flutter_v3).
`flutter analyze`/`flutter test` run in CI on every push/PR to `main`,
along with native builds for both platforms (see the badge above) — please
make sure both pass locally before opening a PR. See
`docs/DEVELOPMENT_LOG.md` for architecture notes if you're changing native
code.

## License

MIT — see `LICENSE`.
