import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapbox_navigation_flutter_v3/mapbox_navigation_flutter_v3_method_channel.dart';
import 'package:mapbox_navigation_flutter_v3/mapbox_navigation_flutter_v3_platform_interface.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelMapboxNavigationFlutterV3 platform = MethodChannelMapboxNavigationFlutterV3();
  const MethodChannel channel = MethodChannel('mapbox_navigation_flutter_v3');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
          return '42';
        });

    expect(await platform.getPlatformVersion(), '42');
  });

  group('initialize', () {
    test('invokes initialize with the access token', () async {
      MethodCall? received;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            received = methodCall;
            return null;
          });

      await platform.initialize(accessToken: 'pk.test');

      expect(received?.method, 'initialize');
      expect(received?.arguments, {'accessToken': 'pk.test'});
    });

    test('wraps a PlatformException as NavigationException', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            throw PlatformException(code: 'NO_TOKEN', message: 'missing token');
          });

      expect(
        () => platform.initialize(accessToken: ''),
        throwsA(isA<NavigationException>()
            .having((e) => e.code, 'code', 'NO_TOKEN')),
      );
    });
  });

  group('startNavigation', () {
    test('serializes waypoints/options and maps the string result back to an enum', () async {
      MethodCall? received;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            received = methodCall;
            return 'arrived';
          });

      final result = await platform.startNavigation(
        waypoints: const [
          NavigationWaypoint(latitude: 26.2, longitude: -98.2, name: 'Home'),
        ],
        options: const NavigationOptions(
          profile: NavigationProfile.cycling,
          language: 'es',
          simulateRoute: true,
        ),
      );

      expect(result, NavigationResult.arrived);
      expect(received?.method, 'startNavigation');
      final args = received!.arguments as Map;
      expect(args['waypoints'], [
        {'latitude': 26.2, 'longitude': -98.2, 'name': 'Home'},
      ]);
      expect(args['options'], {
        'profile': 'cycling',
        'language': 'es',
        'voiceInstructionsEnabled': true,
        'bannerInstructionsEnabled': true,
        'simulateRoute': true,
      });
    });

    test('falls back to NavigationResult.error for an unrecognized result code', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            return 'something-unexpected';
          });

      final result = await platform.startNavigation(
        waypoints: const [NavigationWaypoint(latitude: 0, longitude: 0)],
      );

      expect(result, NavigationResult.error);
    });

    test('wraps a PlatformException as NavigationException', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            throw PlatformException(code: 'NO_ROUTE', message: 'no route found');
          });

      expect(
        () => platform.startNavigation(
          waypoints: const [NavigationWaypoint(latitude: 0, longitude: 0)],
        ),
        throwsA(isA<NavigationException>()
            .having((e) => e.code, 'code', 'NO_ROUTE')),
      );
    });
  });
}
