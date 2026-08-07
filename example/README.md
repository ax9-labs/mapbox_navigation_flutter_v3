# mapbox_navigation_flutter_v3_example

A minimal test harness for the plugin: two buttons, one starts real
GPS-driven navigation, the other starts a simulated route (useful on an
emulator or when you don't want to actually drive anywhere).

## Before you run it

1. **Mapbox downloads token** (Android only, gates fetching the SDK itself):
   add to `~/.gradle/gradle.properties` (create the file if it doesn't
   exist — this is outside any git repo, never commit it):
   ```properties
   MAPBOX_DOWNLOADS_TOKEN=sk.your-secret-token
   ```
   Needs the `DOWNLOADS:READ` scope. Get one at
   [account.mapbox.com/access-tokens](https://account.mapbox.com/access-tokens/).

2. **Mapbox public access token** (used at runtime, passed in at launch —
   never hardcoded):
   ```bash
   flutter run --dart-define=MAPBOX_ACCESS_TOKEN=pk.your-public-token
   ```

Both tokens are required. The downloads token unblocks the Android build;
the public token is what the app actually authenticates map/navigation
requests with.

## What it tests

The example destination is hardcoded near San Francisco
(`example/lib/main.dart`) — swap it for coordinates near you if you want to
test with real GPS movement instead of the simulated route.
