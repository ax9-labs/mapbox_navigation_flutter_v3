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
  });

  final NavigationProfile profile;

  /// BCP-47 language code for voice/banner instructions (e.g. "en", "es").
  final String language;
  final bool voiceInstructionsEnabled;
  final bool bannerInstructionsEnabled;

  /// When true, the native SDK simulates movement along the route instead
  /// of using real GPS - useful for testing without driving anywhere.
  final bool simulateRoute;

  Map<String, Object?> toJson() => {
        'profile': profile.wireValue,
        'language': language,
        'voiceInstructionsEnabled': voiceInstructionsEnabled,
        'bannerInstructionsEnabled': bannerInstructionsEnabled,
        'simulateRoute': simulateRoute,
      };
}

/// Outcome of a [MapboxNavigationFlutterV3.startNavigation] call.
enum NavigationResult {
  /// The user reached the final waypoint.
  arrived,

  /// The user backed out of the native navigation screen before arriving.
  cancelled,

  /// Navigation could not start or failed mid-route (no route found, no
  /// location permission, native SDK error, etc). See
  /// [NavigationException] for the specific cause when this is thrown
  /// instead of returned.
  error,
}

/// Thrown by [MapboxNavigationFlutterV3.startNavigation] when navigation
/// could not be started at all (as opposed to [NavigationResult.error],
/// which can also represent a failure - platform channel errors surface
/// as this exception, everything else as a result value).
class NavigationException implements Exception {
  NavigationException(this.code, this.message);

  final String code;
  final String? message;

  @override
  String toString() => 'NavigationException($code): $message';
}
