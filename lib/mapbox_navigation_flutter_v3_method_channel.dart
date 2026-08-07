import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'mapbox_navigation_flutter_v3_platform_interface.dart';

/// An implementation of [MapboxNavigationFlutterV3Platform] that uses method channels.
class MethodChannelMapboxNavigationFlutterV3 extends MapboxNavigationFlutterV3Platform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('mapbox_navigation_flutter_v3');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<void> initialize({required String accessToken}) async {
    try {
      await methodChannel.invokeMethod<void>('initialize', {
        'accessToken': accessToken,
      });
    } on PlatformException catch (e) {
      throw NavigationException(e.code, e.message);
    }
  }

  @override
  Future<NavigationResult> startNavigation({
    required List<NavigationWaypoint> waypoints,
    NavigationOptions options = const NavigationOptions(),
    List<NavigationMarker> markers = const [],
  }) async {
    try {
      final resultCode = await methodChannel.invokeMethod<String>(
        'startNavigation',
        {
          'waypoints': waypoints.map((w) => w.toJson()).toList(),
          'options': options.toJson(),
          'markers': markers.map((m) => m.toJson()).toList(),
        },
      );
      return NavigationResult.values.firstWhere(
        (r) => r.name == resultCode,
        orElse: () => NavigationResult.error,
      );
    } on PlatformException catch (e) {
      throw NavigationException(e.code, e.message);
    }
  }
}
