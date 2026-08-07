import 'dart:typed_data';

/// A single stop along a navigation route. The last waypoint in the list
/// passed to [MapboxNavigationFlutterV3.startNavigation] is the final
/// destination; earlier ones are intermediate stops.
class NavigationWaypoint {
  const NavigationWaypoint({
    required this.latitude,
    required this.longitude,
    this.name,
  });

  final double latitude;
  final double longitude;

  /// Optional display label shown in the native navigation UI (e.g. in the
  /// arrival banner). Purely cosmetic.
  final String? name;

  Map<String, Object?> toJson() => {
        'latitude': latitude,
        'longitude': longitude,
        'name': name,
      };
}

/// A custom-rendered marker shown on the map during navigation - the
/// specific capability that made Mapbox worth using over Google in the
/// first place (arbitrary bitmap icons, not just a fixed pin set).
///
/// [icon] must be PNG-encoded bytes (any resolution; consider @2x/@3x
/// density for crisp rendering on high-DPI screens - the native side
/// renders it at its native pixel size scaled by [iconScale], it does not
/// do its own density adjustment).
class NavigationMarker {
  NavigationMarker({
    required this.id,
    required this.latitude,
    required this.longitude,
    required this.icon,
    this.iconScale = 1.0,
  })  : assert(id != '', 'id must not be empty'),
        assert(icon.isNotEmpty, 'icon must not be empty'),
        assert(iconScale > 0, 'iconScale must be positive');

  /// Caller-assigned identifier. Not currently used natively beyond
  /// round-tripping (no per-marker update/remove API yet - markers are
  /// set once for the whole navigation session via [MapboxNavigationFlutterV3.startNavigation]),
  /// but required now so that API is additive later without a breaking
  /// change.
  final String id;

  final double latitude;
  final double longitude;
  final Uint8List icon;
  final double iconScale;

  // [icon] crosses the platform channel as-is (not base64-encoded): the
  // Flutter StandardMessageCodec natively supports Uint8List as a first-class
  // binary type, arriving as a plain ByteArray on the Kotlin side - base64
  // would only add ~33% size overhead and an encode/decode step for no
  // benefit here.
  Map<String, Object?> toJson() => {
        'id': id,
        'latitude': latitude,
        'longitude': longitude,
        'icon': icon,
        'iconScale': iconScale,
      };
}

enum NavigationProfile { driving, drivingTraffic, walking, cycling }

extension on NavigationProfile {
  String get wireValue => switch (this) {
        NavigationProfile.driving => 'driving',
        NavigationProfile.drivingTraffic => 'driving-traffic',
        NavigationProfile.walking => 'walking',
        NavigationProfile.cycling => 'cycling',
      };
}

/// Color/shape customization for the turn-by-turn UI (top maneuver banner,
/// bottom trip sheet, route line). Every field is optional - `null` means
/// "use the plugin's built-in default" (a readable, driving-appropriate
/// default theme), so callers only need to override what they actually
/// want to brand.
///
/// Colors are packed ARGB ints, same representation as Flutter's
/// `Color.value` (e.g. pass `myColor.value` from `dart:ui`/material's
/// `Color` without this package depending on Flutter's rendering layer
/// directly).
class TurnByTurnTheme {
  const TurnByTurnTheme({
    this.primaryInstructionColor,
    this.primaryInstructionForegroundColor,
    this.secondaryInstructionColor,
    this.secondaryInstructionForegroundColor,
    this.surfaceColor,
    this.surfaceForegroundColor,
    this.routeColor,
    this.routeCasingColor,
    this.trafficLowColor,
    this.trafficModerateColor,
    this.trafficHeavyColor,
    this.trafficSevereColor,
    this.dividerColor,
    this.secondaryTextColor,
    this.maneuverIconColor,
    this.sheetHandleColor,
    this.cornerRadius,
    this.elevation,
  });

  /// Top maneuver banner background.
  final int? primaryInstructionColor;

  /// Top maneuver banner text/icon color.
  final int? primaryInstructionForegroundColor;

  /// Next-maneuver preview background.
  final int? secondaryInstructionColor;

  /// Next-maneuver preview text/icon color.
  final int? secondaryInstructionForegroundColor;

  /// Bottom trip sheet background.
  final int? surfaceColor;

  /// Bottom trip sheet primary text/icon color.
  final int? surfaceForegroundColor;

  final int? routeColor;
  final int? routeCasingColor;

  final int? trafficLowColor;
  final int? trafficModerateColor;
  final int? trafficHeavyColor;
  final int? trafficSevereColor;

  final int? dividerColor;
  final int? secondaryTextColor;
  final int? maneuverIconColor;

  /// The bottom sheet's drag handle.
  final int? sheetHandleColor;

  /// Corner radius (logical px) applied to the top banner and bottom sheet
  /// surfaces.
  final double? cornerRadius;

  /// Elevation/shadow (logical px) applied to the top banner and bottom
  /// sheet surfaces.
  final double? elevation;

  Map<String, Object?> toJson() => {
        'primaryInstructionColor': primaryInstructionColor,
        'primaryInstructionForegroundColor': primaryInstructionForegroundColor,
        'secondaryInstructionColor': secondaryInstructionColor,
        'secondaryInstructionForegroundColor': secondaryInstructionForegroundColor,
        'surfaceColor': surfaceColor,
        'surfaceForegroundColor': surfaceForegroundColor,
        'routeColor': routeColor,
        'routeCasingColor': routeCasingColor,
        'trafficLowColor': trafficLowColor,
        'trafficModerateColor': trafficModerateColor,
        'trafficHeavyColor': trafficHeavyColor,
        'trafficSevereColor': trafficSevereColor,
        'dividerColor': dividerColor,
        'secondaryTextColor': secondaryTextColor,
        'maneuverIconColor': maneuverIconColor,
        'sheetHandleColor': sheetHandleColor,
        'cornerRadius': cornerRadius,
        'elevation': elevation,
      };
}

/// Feature toggles for the turn-by-turn UI - which pieces of the screen to
/// show, and a few behavioral switches. Defaults match the full
/// production-quality experience; turn things off individually rather than
/// needing an all-or-nothing "minimal UI" flag.
class TurnByTurnUiOptions {
  const TurnByTurnUiOptions({
    this.showTopBanner = true,
    this.showNextManeuver = true,
    this.showBottomSheet = true,
    this.showTraffic = true,
    this.showTrafficSignals = false,
    this.showRoadLabels = true,
    this.showRecenterButton = true,
    this.showRouteOverviewButton = true,
    this.enableExpandableBottomSheet = true,
    this.confirmBeforeExitNavigation = false,
    this.showArrivalTime = true,
    this.showRemainingDistance = true,
    this.showRemainingDuration = true,
    this.enableNavigationCamera = true,
    this.enableManeuverAnimations = true,
  });

  final bool showTopBanner;
  final bool showNextManeuver;
  final bool showBottomSheet;
  final bool showTraffic;

  /// Off by default: reliable traffic-signal/intersection-signal data isn't
  /// currently exposed by the Mapbox Navigation SDK in a way this plugin
  /// can trust, so turning this on is a no-op today rather than fabricated
  /// markers - see README for the extensibility point this reserves.
  final bool showTrafficSignals;

  final bool showRoadLabels;
  final bool showRecenterButton;
  final bool showRouteOverviewButton;
  final bool enableExpandableBottomSheet;

  /// When true, tapping the stop-navigation control shows a confirmation
  /// dialog first instead of ending the session immediately.
  final bool confirmBeforeExitNavigation;

  final bool showArrivalTime;
  final bool showRemainingDistance;
  final bool showRemainingDuration;

  /// When false, the camera stays wherever it was left (no
  /// following/overview/recenter behavior) - for embedding scenarios that
  /// want to drive their own camera.
  final bool enableNavigationCamera;

  final bool enableManeuverAnimations;

  Map<String, Object?> toJson() => {
        'showTopBanner': showTopBanner,
        'showNextManeuver': showNextManeuver,
        'showBottomSheet': showBottomSheet,
        'showTraffic': showTraffic,
        'showTrafficSignals': showTrafficSignals,
        'showRoadLabels': showRoadLabels,
        'showRecenterButton': showRecenterButton,
        'showRouteOverviewButton': showRouteOverviewButton,
        'enableExpandableBottomSheet': enableExpandableBottomSheet,
        'confirmBeforeExitNavigation': confirmBeforeExitNavigation,
        'showArrivalTime': showArrivalTime,
        'showRemainingDistance': showRemainingDistance,
        'showRemainingDuration': showRemainingDuration,
        'enableNavigationCamera': enableNavigationCamera,
        'enableManeuverAnimations': enableManeuverAnimations,
      };
}

class NavigationOptions {
  const NavigationOptions({
    this.profile = NavigationProfile.drivingTraffic,
    this.language = 'en',
    this.voiceInstructionsEnabled = true,
    this.bannerInstructionsEnabled = true,
    this.simulateRoute = false,
    this.arrivalDistanceMeters = 25,
    this.simulateSpeedMultiplier = 3,
    this.theme = const TurnByTurnTheme(),
    this.uiOptions = const TurnByTurnUiOptions(),
  })  : assert(arrivalDistanceMeters > 0, 'arrivalDistanceMeters must be positive'),
        assert(simulateSpeedMultiplier > 0, 'simulateSpeedMultiplier must be positive');

  final NavigationProfile profile;

  /// BCP-47 language code for voice/banner instructions (e.g. "en", "es").
  final String language;
  final bool voiceInstructionsEnabled;
  final bool bannerInstructionsEnabled;

  /// When true, the native SDK simulates movement along the route instead
  /// of using real GPS - useful for testing without driving anywhere.
  final bool simulateRoute;

  /// How close (in meters) the user must get to the final waypoint before
  /// [NavigationResult.arrived] fires. The 25m default suits driving; a
  /// tighter value is likely more appropriate for
  /// [NavigationProfile.walking], and a looser one for open highway
  /// driving where GPS drift is larger relative to road width.
  final double arrivalDistanceMeters;

  /// Playback speed multiplier used only when [simulateRoute] is true -
  /// has no effect on real-GPS sessions. Dev/QA convenience for shortening
  /// how long a simulated drive takes to observe.
  final double simulateSpeedMultiplier;

  /// Colors/shape for the turn-by-turn UI. See [TurnByTurnTheme].
  final TurnByTurnTheme theme;

  /// Which pieces of the turn-by-turn UI to show and a few behavior
  /// switches. See [TurnByTurnUiOptions].
  final TurnByTurnUiOptions uiOptions;

  Map<String, Object?> toJson() => {
        'profile': profile.wireValue,
        'language': language,
        'voiceInstructionsEnabled': voiceInstructionsEnabled,
        'bannerInstructionsEnabled': bannerInstructionsEnabled,
        'simulateRoute': simulateRoute,
        'arrivalDistanceMeters': arrivalDistanceMeters,
        'simulateSpeedMultiplier': simulateSpeedMultiplier,
        'theme': theme.toJson(),
        'uiOptions': uiOptions.toJson(),
      };
}

/// Outcome of a [MapboxNavigationFlutterV3.startNavigation] call that
/// actually ran to some conclusion. Any failure - couldn't start at all,
/// or failed partway through (no route found, location unavailable,
/// native SDK error) - is instead thrown as [NavigationException]; there
/// is deliberately no `NavigationResult.error` value, so callers only
/// need to branch on this enum for genuine outcomes and use try/catch for
/// everything that went wrong. See [NavigationException] for the
/// distinguishing error codes.
enum NavigationResult {
  /// The user reached the final waypoint.
  arrived,

  /// The user backed out of the native navigation screen before arriving.
  cancelled,
}

/// Thrown by [MapboxNavigationFlutterV3.startNavigation] for any failure -
/// whether navigation couldn't start at all (e.g. missing location
/// permission, no access token configured) or failed partway through
/// (e.g. no route found, location became unavailable). [code] is a
/// stable, switchable identifier (e.g. `"NO_WAYPOINTS"`,
/// `"LOCATION_UNAVAILABLE"`, `"ROUTE_REQUEST_FAILED"`); [message] is a
/// human-readable detail for logging, not guaranteed stable across SDK
/// versions.
class NavigationException implements Exception {
  NavigationException(this.code, this.message);

  final String code;
  final String? message;

  @override
  String toString() => 'NavigationException($code): $message';
}
