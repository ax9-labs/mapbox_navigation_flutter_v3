import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:mapbox_navigation_flutter_v3/mapbox_navigation_flutter_v3.dart';
import 'package:mapbox_navigation_flutter_v3/mapbox_navigation_flutter_v3_platform_interface.dart';
import 'package:mapbox_navigation_flutter_v3/mapbox_navigation_flutter_v3_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockMapboxNavigationFlutterV3Platform
    with MockPlatformInterfaceMixin
    implements MapboxNavigationFlutterV3Platform {
  String? initializedWithToken;
  List<NavigationWaypoint>? lastWaypoints;
  NavigationOptions? lastOptions;
  List<NavigationMarker>? lastMarkers;
  NavigationResult resultToReturn = NavigationResult.arrived;

  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  Future<void> initialize({required String accessToken}) async {
    initializedWithToken = accessToken;
  }

  @override
  Future<NavigationResult> startNavigation({
    required List<NavigationWaypoint> waypoints,
    NavigationOptions options = const NavigationOptions(),
    List<NavigationMarker> markers = const [],
  }) async {
    lastWaypoints = waypoints;
    lastOptions = options;
    lastMarkers = markers;
    return resultToReturn;
  }
}

void main() {
  final MapboxNavigationFlutterV3Platform initialPlatform = MapboxNavigationFlutterV3Platform.instance;

  test('$MethodChannelMapboxNavigationFlutterV3 is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelMapboxNavigationFlutterV3>());
  });

  test('getPlatformVersion', () async {
    MapboxNavigationFlutterV3 mapboxNavigationFlutterV3Plugin = MapboxNavigationFlutterV3();
    MockMapboxNavigationFlutterV3Platform fakePlatform = MockMapboxNavigationFlutterV3Platform();
    MapboxNavigationFlutterV3Platform.instance = fakePlatform;

    expect(await mapboxNavigationFlutterV3Plugin.getPlatformVersion(), '42');
  });

  test('initialize forwards the access token to the platform implementation', () async {
    final plugin = MapboxNavigationFlutterV3();
    final fakePlatform = MockMapboxNavigationFlutterV3Platform();
    MapboxNavigationFlutterV3Platform.instance = fakePlatform;

    await plugin.initialize(accessToken: 'pk.test-token');

    expect(fakePlatform.initializedWithToken, 'pk.test-token');
  });

  test('startNavigation forwards waypoints/options and returns the result', () async {
    final plugin = MapboxNavigationFlutterV3();
    final fakePlatform = MockMapboxNavigationFlutterV3Platform()
      ..resultToReturn = NavigationResult.cancelled;
    MapboxNavigationFlutterV3Platform.instance = fakePlatform;
    const waypoints = [NavigationWaypoint(latitude: 1, longitude: 2, name: 'Safe Zone')];
    const options = NavigationOptions(profile: NavigationProfile.walking, simulateRoute: true);

    final result = await plugin.startNavigation(waypoints: waypoints, options: options);

    expect(result, NavigationResult.cancelled);
    expect(fakePlatform.lastWaypoints, waypoints);
    expect(fakePlatform.lastOptions, options);
    expect(fakePlatform.lastMarkers, isEmpty);
  });

  test('startNavigation forwards markers to the platform implementation', () async {
    final plugin = MapboxNavigationFlutterV3();
    final fakePlatform = MockMapboxNavigationFlutterV3Platform();
    MapboxNavigationFlutterV3Platform.instance = fakePlatform;
    final markers = [
      NavigationMarker(
        id: 'incident-1',
        latitude: 1,
        longitude: 2,
        icon: Uint8List.fromList([1, 2, 3]),
      ),
    ];

    await plugin.startNavigation(
      waypoints: const [NavigationWaypoint(latitude: 1, longitude: 2)],
      markers: markers,
    );

    expect(fakePlatform.lastMarkers, markers);
  });
}
