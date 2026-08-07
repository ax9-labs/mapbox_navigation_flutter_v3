# Turn-by-turn UI redesign — deliverables report

## Architecture note (read this first)

The redesign request was written in Flutter-widget terms (`Stack`,
`SafeArea`, `MediaQuery`, a Dart presentation state observed by widgets).
This plugin's navigation screen is, and remains, **native Android** — a
full-screen `NavigationActivity` (Kotlin) launched via
`startActivityForResult`, not a Flutter widget tree. That's a deliberate
architecture choice (see README's "Why composed instead of a drop-in
screen"), not an oversight, and the user confirmed keeping it rather than
migrating to a Flutter-rendered overlay + platform-view map (a much larger,
separate project). Every requirement below was reinterpreted into its
native-Android equivalent:

| Spec vocabulary | Native equivalent used |
|---|---|
| `Stack` composition | `CoordinatorLayout` with overlaid children |
| `SafeArea` | `WindowInsetsCompat` applied at runtime |
| `MediaQuery`/responsive widgets | `ConstraintLayout`/`LinearLayout` with `wrap_content`/`match_parent`, no fixed pixel positions |
| Dart presentation state + widgets | `RouteProgressObserver`/`MapboxManeuverApi`/`MapboxTripProgressApi` (already existed) driving native `View`s directly |
| `DraggableScrollableSheet` states | `BottomSheetBehavior` (COLLAPSED/EXPANDED) |
| `TurnByTurnTheme`/`TurnByTurnUiOptions` (Dart config objects) | Implemented **as specified** — these two are genuinely new public Dart API, decoded into native equivalents |

## Files created

- `lib/src/models.dart` (extended, not new) — `TurnByTurnTheme`, `TurnByTurnUiOptions`
- `android/.../TurnByTurnTheme.kt` — native decode of the two Dart config objects
- `android/.../NavigationTheme.kt` — resolves theme against built-in defaults, builds themed backgrounds, applies to reusable Mapbox views
- `android/.../NavigationBottomSheetController.kt` — owns the trip sheet (new)
- `android/src/main/res/values/mnfv3_colors.xml` + `values-night/mnfv3_colors.xml` — default light/dark theme
- `android/src/main/res/values/mnfv3_dimens.xml`
- `android/src/main/res/drawable/mnfv3_ic_{stop,recenter,overview,arrived_check}.xml` — original vector icons (see "Maneuver icon approach" below for why these are the *only* custom icons)
- `android/src/main/res/drawable/mnfv3_bg_sheet_handle.xml`
- `android/src/test/kotlin/.../NavigationIntentCodecTest.kt` (extended) — theme/uiOptions decode tests

## Files modified

- `android/src/main/res/layout/mnfv3_activity_navigation.xml` — full rewrite: `CoordinatorLayout` root, top overlay (maneuver card + next-maneuver preview + reroute/GPS pills), recenter button, `BottomSheetBehavior`-driven trip sheet (stop button, duration/distance/arrival, action button, detailed maneuver row, arrival panel)
- `android/.../NavigationActivity.kt` — camera state observer + recenter wiring, WindowInsets → camera-padding pipeline, GPS watchdog, arrival panel + delayed finish, theme application, all gated by `TurnByTurnUiOptions`
- `android/.../NavigationIntentCodec.kt` — `NavigationStartOptions` extended with `theme`/`uiOptions`
- `android/build.gradle.kts` — added `androidx.coordinatorlayout` + `com.google.android.material` (for `BottomSheetBehavior`)
- `lib/mapbox_navigation_flutter_v3.dart` — exports the two new theme/config classes
- `README.md` — architecture/status updates

## Existing state/controllers reused (not duplicated)

Per the "do not duplicate" instructions, nothing here recomputes anything
Mapbox already provides:

- **Route progress, maneuver data, ETA/distance formatting**: `RouteProgressObserver` (existed), `MapboxManeuverApi.getManeuvers()` (existed), `MapboxTripProgressApi.getTripProgress()` (existed) — the new trip summary row reads `TripProgressUpdateValue.formatter.getTimeRemaining()/getDistanceRemaining()/getEstimatedTimeToArrival()`, Mapbox's own locale/unit-aware formatters, not manual formatting.
- **Maneuver icons**: `MapboxTurnIconManeuver` (Mapbox's own reusable icon-rendering `ImageView`) is instantiated twice more (next-maneuver preview, detailed maneuver row) beyond the existing `MapboxManeuverView` instance. **No custom `ManeuverIconResolver` was built** — Mapbox's own icon renderer already covers every maneuver type/modifier/roundabout-degree combination the spec lists, and duplicating that logic would violate "do not duplicate maneuver calculations."
- **Camera FOLLOWING/OVERVIEW/FREE**: `NavigationCamera.requestNavigationCameraToFollowing()/ToOverview()/ToIdle()` (all pre-existing SDK API, not new). "FREE mode" was already implicit — `NavigationBasicGesturesHandler` (already wired) auto-transitions the camera to `IDLE` on user gesture; this pass only added the recenter *button* and a `NavigationCameraStateChangedObserver` to show/hide it.
- **Route line + traffic coloring**: `MapboxRouteLineApi`/`MapboxRouteLineView` (existed). Congestion coloring uses Mapbox's own `RouteLineColorResources` fed from the theme — the `congestion_numeric` route annotation was already being requested (`applyDefaultNavigationOptions()`), so no new route data plumbing was needed, only supplying which colors to use per congestion level.
- **Rerouting**: fully automatic via `MapboxNavigation`'s default `RerouteController` — unchanged from the prior session's work.

## New widgets/components created

- `NavigationBottomSheetController` — owns the trip sheet's views, theming, and two render methods (`updateTripSummary`, `updateDetailedManeuver`) plus arrival-panel swap. Deliberately pulled out of `NavigationActivity` for separation of concerns (the Activity was already large before this pass).
- Next-maneuver preview ("Then ↱ ...") — new compact `LinearLayout` + `MapboxTurnIconManeuver`, visually subordinate to the primary banner per spec.
- Recenter control — new circular `ImageButton`, visibility driven by camera state.
- Arrival panel — new view swapped in over the trip sheet content on arrival (checkmark + "You've arrived"), visible for 1.5s before the session actually ends (previously it ended instantly on crossing the arrival threshold).
- GPS-unavailable pill — new, driven by a watchdog comparing time-since-last-location-update against a threshold; never fabricates movement, only signals staleness.

## New public configuration APIs (Dart)

Both are optional fields on the existing `NavigationOptions` (backward
compatible — no existing call site breaks):

- `TurnByTurnTheme` — 18 fields (colors + corner radius + elevation), every field nullable/optional, `null` = built-in default.
- `TurnByTurnUiOptions` — 15 boolean/behavior flags exactly matching the spec's list (`showTopBanner`, `showNextManeuver`, `showBottomSheet`, `showTraffic`, `showTrafficSignals`, `showRoadLabels`, `showRecenterButton`, `showRouteOverviewButton`, `enableExpandableBottomSheet`, `confirmBeforeExitNavigation`, `showArrivalTime`, `showRemainingDistance`, `showRemainingDuration`, `enableNavigationCamera`, `enableManeuverAnimations`).

## Maneuver icon mapping approach

**Not a custom resolver.** `MapboxTurnIconManeuver.renderPrimaryTurnIcon(PrimaryManeuver)` is Mapbox's own reusable icon view — it already resolves every maneuver type/modifier/roundabout-degree combination the spec asked for support of (straight, continue, slight/sharp left/right, U-turns, merge, fork, ramp, exit, roundabout, rotary, arrive, depart, end-of-road, lane guidance). This plugin now uses three instances of it: inside `MapboxManeuverView` (top banner, pre-existing), and two new standalone instances (next-maneuver preview, detailed maneuver row) - all fed from the exact same `Maneuver` list returned by one `MapboxManeuverApi.getManeuvers()` call per progress tick, so all three stay in sync by construction.

## Navigation camera changes

- Registered a `NavigationCameraStateChangedObserver` to drive recenter-button visibility (shown whenever state isn't `FOLLOWING`/`TRANSITION_TO_FOLLOWING`).
- `viewportDataSource.followingPadding`/`overviewPadding` are now set from real measured view heights (top overlay + bottom sheet peek height) plus `WindowInsetsCompat` system-bar insets, recomputed on layout and inset changes — this is the "account for header/bottom-sheet height, don't hide maneuver geometry" requirement, using the SDK's own padding mechanism rather than a custom camera implementation.
- Trip-action button toggles `FOLLOWING ⇄ OVERVIEW` (default behavior per spec: "should preferably be route overview").

## Route/traffic rendering changes

- `MapboxRouteLineViewOptions` now receives a `RouteLineColorResources` built from the theme's `route*`/`traffic*Color` fields (previously unstyled/default colors).
- No changes to route geometry/trimming behavior — that was already handled by the existing `MapboxRouteLineApi`/`RoutesObserver` pipeline and was left untouched per "preserve existing navigation functionality."

## Bottom drawer implementation

Real `com.google.android.material.bottomsheet.BottomSheetBehavior`, not a
hand-rolled drag implementation. Two practical states were implemented,
not three:

- **COLLAPSED** (peek): trip summary row only (stop, duration, distance•arrival, action button) — matches spec's "remain glanceable" requirement.
- **EXPANDED**: adds the detailed maneuver row.

The spec's third "EXPANDED (route steps list, settings, alternate
routes...)" tier was **not built** — that's a materially larger feature
(a route-steps list UI, a settings panel, alternate-route selection UI)
that doesn't have an obvious 1:1 native-Android mapping without further
product decisions about what belongs in it. `enableExpandableBottomSheet:
false` collapses this to a single fixed state showing both rows, for
consumers who don't want drag interaction at all.

## Accessibility improvements

- `contentDescription` on every interactive control (stop, recenter,
  action/overview button), sourced from string resources so they're
  translatable.
- Dynamic announcement text on the primary maneuver card and detailed
  maneuver row, matching the spec's example format ("Next maneuver: turn
  right onto Ash Street in 120 meters") via
  `R.string.mnfv3_maneuver_announcement_template`.
- Bottom sheet expand/collapse state changes update the sheet's
  `contentDescription` ("Expand trip details" / "Collapse trip details").
- All new tappable controls sized to the `mnfv3_min_touch_target` (48dp)
  minimum.
- **Not done**: explicit reduced-motion handling (checking
  `Settings.Global.ANIMATOR_DURATION_SCALE`) — sidestepped rather than
  solved, because no custom animations were added in the first place
  (visibility toggles are instant; `BottomSheetBehavior`'s built-in slide
  already respects the system animator scale on its own). High-contrast
  mode was not explicitly tested.

## Responsive design decisions

- No fixed pixel/dp positions anywhere in the new layout — every new view
  uses `wrap_content`/`match_parent` with `ConstraintLayout`/`LinearLayout`
  weight-based sizing.
- Top overlay and bottom sheet padding/margins come from
  `WindowInsetsCompat` at runtime (status bar, cutouts, gesture nav), not
  hardcoded assumptions.
- **Not independently tested**: tablet layouts, landscape orientation, and
  extreme font-scaling. The layout choices (no fixed widths, `wrap_content`
  heights) should degrade reasonably, but this wasn't verified on a
  tablet/landscape emulator profile this session — flagged for the
  physical-device runbook.

## Mapbox data confirmed unavailable

- **Traffic signal/intersection locations**: no reliable, consistently-populated field for this was found in the Directions API response data this SDK exposes. Per the spec's own instruction ("do not fabricate traffic lights... design as optional/extensible"), `showTrafficSignals` exists as a config flag but renders nothing when true today — documented here rather than silently no-op.

## Features intentionally made optional / deferred

- Route-steps-list / settings / alternate-route-selection panel (third bottom-sheet tier) — see "Bottom drawer implementation" above.
- Traffic signal markers — extension point only, no rendering (data unavailable).
- Reduced-motion / high-contrast explicit handling — not implemented, relies on system defaults + absence of custom animation.

## Breaking changes

**None.** Both new Dart types are optional fields with defaults on the
existing `NavigationOptions`; every existing call site continues to
compile and behave identically if it doesn't pass `theme`/`uiOptions`.

## Real bug found and fixed during this pass (worth flagging on its own)

`ManeuverViewOptions.Builder.maneuverBackgroundColor(Int)` looks like a
plain ARGB setter from its signature, but Mapbox's SDK internally treats
that `Int` as a `@ColorRes` resource ID and calls
`ContextCompat.getColor(context, value)` on it — which crashed
immediately (`Resources.NotFoundException`) the first time a real
Dart-configurable ARGB value was passed through it. Worked around by
never calling that API for backgrounds; our own themed card (which
`MapboxManeuverView` sits inside, transparent) supplies the color instead.

## Follow-up improvements recommended

1. Physical-device verification of landscape/tablet/font-scaling layouts (see `PHYSICAL_DEVICE_TESTING.md`).
2. Decide whether the third bottom-sheet tier (route steps, settings, alternates) is wanted, and scope it as its own pass if so.
3. Confirm the stop button's tap target with real-device testing — emulator touch-input reliability degraded during this session's later testing (likely emulator resource exhaustion after a long session, not a UI bug: the equivalent `finishWithResult(RESULT_CANCELLED)` path was verified working via the hardware back button on the same build).
4. Consider exposing `NavigationTheme`'s resolved values as a small internal unit-testable pure function (currently resolution + Android `Context`/resource access are combined in one class) for the theme-resolution logic specifically, mirroring the `MarkerDecoder`/`NavigationIntentCodec` testability pattern used elsewhere in this plugin.
