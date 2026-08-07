import 'mapbox_navigation_flutter_v3_platform_interface.dart';

export 'mapbox_navigation_flutter_v3_platform_interface.dart' show NavigationWaypoint, NavigationOptions, NavigationProfile, NavigationResult, NavigationException, NavigationMarker;

class MapboxNavigationFlutterV3 {
  Future<String?> getPlatformVersion() {
    return MapboxNavigationFlutterV3Platform.instance.getPlatformVersion();
  }

  /// Configures the native Mapbox SDKs with the given public access token.
  /// Call this once (e.g. at app startup) before [startNavigation].
  Future<void> initialize({required String accessToken}) {
    return MapboxNavigationFlutterV3Platform.instance.initialize(
      accessToken: accessToken,
    );
  }

  /// Launches full-screen native turn-by-turn navigation - a maneuver
  /// banner, voice instructions, and trip progress bar composed from
  /// Mapbox Navigation SDK v3's components (Android; iOS not yet
  /// implemented) - from the device's current location through
  /// [waypoints]. Suspends until the user arrives at the final waypoint,
  /// backs out of the navigation screen, or navigation errors.
  ///
  /// [markers] are rendered on the map for the duration of the session -
  /// see [NavigationMarker] for the custom-bitmap-icon support this
  /// exists for.
  ///
  /// Throws [NavigationException] if navigation could not be started at
  /// all (e.g. missing location permission, no access token configured).
  Future<NavigationResult> startNavigation({
    required List<NavigationWaypoint> waypoints,
    NavigationOptions options = const NavigationOptions(),
    List<NavigationMarker> markers = const [],
  }) {
    return MapboxNavigationFlutterV3Platform.instance.startNavigation(
      waypoints: waypoints,
      options: options,
      markers: markers,
    );
  }
}
