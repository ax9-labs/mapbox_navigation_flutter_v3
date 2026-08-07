import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'mapbox_navigation_flutter_v3_method_channel.dart';
import 'src/models.dart';

export 'src/models.dart';

abstract class MapboxNavigationFlutterV3Platform extends PlatformInterface {
  /// Constructs a MapboxNavigationFlutterV3Platform.
  MapboxNavigationFlutterV3Platform() : super(token: _token);

  static final Object _token = Object();

  static MapboxNavigationFlutterV3Platform _instance = MethodChannelMapboxNavigationFlutterV3();

  /// The default instance of [MapboxNavigationFlutterV3Platform] to use.
  ///
  /// Defaults to [MethodChannelMapboxNavigationFlutterV3].
  static MapboxNavigationFlutterV3Platform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [MapboxNavigationFlutterV3Platform] when
  /// they register themselves.
  static set instance(MapboxNavigationFlutterV3Platform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  /// Configures the native Mapbox SDKs with the given public access token.
  /// Must be called once, before [startNavigation].
  Future<void> initialize({required String accessToken}) {
    throw UnimplementedError('initialize() has not been implemented.');
  }

  /// Launches the native full-screen turn-by-turn navigation UI to the
  /// given [waypoints], starting from the device's current location.
  /// Suspends until the user arrives, cancels, or navigation errors.
  ///
  /// [markers] are rendered on the map for the duration of the navigation
  /// session (both on the base map and, where supported, alongside the
  /// turn-by-turn guidance view) - see [NavigationMarker].
  Future<NavigationResult> startNavigation({
    required List<NavigationWaypoint> waypoints,
    NavigationOptions options = const NavigationOptions(),
    List<NavigationMarker> markers = const [],
  }) {
    throw UnimplementedError('startNavigation() has not been implemented.');
  }
}
