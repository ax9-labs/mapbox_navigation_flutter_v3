## 0.1.0

Both platforms implemented and verified running (not just compiled) — see
README.md "Status" section for the full detail.

- **Android**: full happy-path matrix verified on a real emulator (route
  request, simulated + real-GPS-driven trips, arrival detection, cancel,
  malformed-input handling) — turn-by-turn guidance, custom markers,
  automatic reroute feedback, and a themed production-style UI, all built
  on Mapbox Navigation SDK v3's standalone components.
- **iOS**: implemented on top of Mapbox's own turnkey
  `NavigationViewController`, themed to match. A full simulated-route
  session — route request, live map/route/puck, real-time maneuver
  banner/trip progress, arrival, clean dismissal back to
  `NavigationResult.arrived` — verified end to end on the iOS Simulator
  with a real Mapbox token.
- Shared `TurnByTurnTheme`/`TurnByTurnUiOptions` for customizing the
  turn-by-turn screen's colors and which pieces of it show, on both
  platforms.
- Still open: physical-device verification on both platforms (only
  emulator/simulator tested so far) and the real-GPS path on iOS. See
  README.md "Follow-up work" for the full list.

## 0.0.1

Initial scaffold: Dart API only, native platforms not yet implemented.
