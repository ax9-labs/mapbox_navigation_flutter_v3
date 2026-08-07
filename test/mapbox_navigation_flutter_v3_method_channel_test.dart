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
      expect(
        args['options'],
        allOf([
          containsPair('profile', 'cycling'),
          containsPair('language', 'es'),
          containsPair('voiceInstructionsEnabled', true),
          containsPair('bannerInstructionsEnabled', true),
          containsPair('simulateRoute', true),
          containsPair('arrivalDistanceMeters', 25.0),
          containsPair('simulateSpeedMultiplier', 3.0),
          containsPair('theme', isA<Map>()),
          containsPair('uiOptions', isA<Map>()),
        ]),
      );
      expect(args['markers'], isEmpty);
    });

    test('serializes custom arrivalDistanceMeters/simulateSpeedMultiplier', () async {
      MethodCall? received;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            received = methodCall;
            return 'arrived';
          });

      await platform.startNavigation(
        waypoints: const [NavigationWaypoint(latitude: 0, longitude: 0)],
        options: const NavigationOptions(
          arrivalDistanceMeters: 10,
          simulateSpeedMultiplier: 1.5,
        ),
      );

      final args = received!.arguments as Map;
      expect(args['options'], containsPair('arrivalDistanceMeters', 10.0));
      expect(args['options'], containsPair('simulateSpeedMultiplier', 1.5));
    });

    test('serializes theme and uiOptions overrides', () async {
      MethodCall? received;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            received = methodCall;
            return 'arrived';
          });

      await platform.startNavigation(
        waypoints: const [NavigationWaypoint(latitude: 0, longitude: 0)],
        options: const NavigationOptions(
          theme: TurnByTurnTheme(primaryInstructionColor: 0xFF0057FF, cornerRadius: 16),
          uiOptions: TurnByTurnUiOptions(
            showTrafficSignals: true,
            confirmBeforeExitNavigation: true,
            showRecenterButton: false,
          ),
        ),
      );

      final args = received!.arguments as Map;
      final theme = args['options']['theme'] as Map;
      final uiOptions = args['options']['uiOptions'] as Map;
      expect(theme, containsPair('primaryInstructionColor', 0xFF0057FF));
      expect(theme, containsPair('cornerRadius', 16.0));
      expect(uiOptions, containsPair('showTrafficSignals', true));
      expect(uiOptions, containsPair('confirmBeforeExitNavigation', true));
      expect(uiOptions, containsPair('showRecenterButton', false));
    });

    test('forwards marker icon bytes as-is (no base64 encoding)', () async {
      MethodCall? received;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            received = methodCall;
            return 'arrived';
          });
      final iconBytes = Uint8List.fromList([137, 80, 78, 71]);

      await platform.startNavigation(
        waypoints: const [NavigationWaypoint(latitude: 0, longitude: 0)],
        markers: [
          NavigationMarker(
            id: 'incident-1',
            latitude: 26.2,
            longitude: -98.2,
            icon: iconBytes,
            iconScale: 1.5,
          ),
        ],
      );

      final args = received!.arguments as Map;
      expect(args['markers'], [
        {
          'id': 'incident-1',
          'latitude': 26.2,
          'longitude': -98.2,
          'icon': iconBytes,
          'iconScale': 1.5,
        },
      ]);
    });

    test('throws NavigationException for an unrecognized result code', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
            return 'something-unexpected';
          });

      expect(
        () => platform.startNavigation(
          waypoints: const [NavigationWaypoint(latitude: 0, longitude: 0)],
        ),
        throwsA(isA<NavigationException>()
            .having((e) => e.code, 'code', 'UNEXPECTED_RESULT')),
      );
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
