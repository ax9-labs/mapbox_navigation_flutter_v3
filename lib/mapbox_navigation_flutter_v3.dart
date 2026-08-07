import 'mapbox_navigation_flutter_v3_platform_interface.dart';

export 'mapbox_navigation_flutter_v3_platform_interface.dart' show NavigationWaypoint, NavigationOptions, NavigationProfile, NavigationResult, NavigationException;

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

  /// Launches full-screen native turn-by-turn navigation (Mapbox's
  /// Drop-In UI on both platforms) from the device's current location
  /// through [waypoints]. Suspends until the user arrives at the final
  /// waypoint, backs out of the navigation screen, or navigation errors.
  ///
  /// Throws [NavigationException] if navigation could not be started at
  /// all (e.g. missing location permission, no access token configured).
  Future<NavigationResult> startNavigation({
    required List<NavigationWaypoint> waypoints,
    NavigationOptions options = const NavigationOptions(),
  }) {
    return MapboxNavigationFlutterV3Platform.instance.startNavigation(
      waypoints: waypoints,
      options: options,
    );
  }
}
