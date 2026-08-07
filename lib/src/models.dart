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

class NavigationOptions {
  const NavigationOptions({
    this.profile = NavigationProfile.drivingTraffic,
    this.language = 'en',
    this.voiceInstructionsEnabled = true,
    this.bannerInstructionsEnabled = true,
    this.simulateRoute = false,
    this.arrivalDistanceMeters = 25,
    this.simulateSpeedMultiplier = 3,
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

  Map<String, Object?> toJson() => {
        'profile': profile.wireValue,
        'language': language,
        'voiceInstructionsEnabled': voiceInstructionsEnabled,
        'bannerInstructionsEnabled': bannerInstructionsEnabled,
        'simulateRoute': simulateRoute,
        'arrivalDistanceMeters': arrivalDistanceMeters,
        'simulateSpeedMultiplier': simulateSpeedMultiplier,
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
