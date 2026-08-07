import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:mapbox_navigation_flutter_v3/mapbox_navigation_flutter_v3.dart';
import 'package:permission_handler/permission_handler.dart';

/// Pass your public Mapbox access token at build/run time - never commit
/// one here. e.g.:
///   flutter run --dart-define=MAPBOX_ACCESS_TOKEN=pk.your-token
const _accessToken = String.fromEnvironment('MAPBOX_ACCESS_TOKEN');

/// A short (~500m) drive, close enough to the device's actual last-known
/// location to let arrival detection fire quickly during testing. Swap
/// for coordinates near wherever your test device/emulator actually is.
const _testDestination = NavigationWaypoint(
  latitude: 26.2075,
  longitude: -98.2270,
  name: 'Test destination',
);

/// A small red pin PNG, used to verify custom marker rendering end to end
/// without shipping an image asset in this test harness.
const _markerIconBase64 =
    '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';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _plugin = MapboxNavigationFlutterV3();

  String _status = _accessToken.isEmpty
      ? 'No MAPBOX_ACCESS_TOKEN provided - see comment in main.dart'
      : 'Ready';
  bool _busy = false;

  Future<void> _startNavigation({required bool simulate}) async {
    if (_accessToken.isEmpty) {
      setState(() => _status = 'No MAPBOX_ACCESS_TOKEN provided - see comment in main.dart');
      return;
    }

    setState(() {
      _busy = true;
      _status = 'Requesting location permission...';
    });

    final permission = await Permission.locationWhenInUse.request();
    if (!permission.isGranted) {
      setState(() {
        _busy = false;
        _status = 'Location permission denied';
      });
      return;
    }

    try {
      setState(() => _status = 'Initializing Mapbox...');
      await _plugin.initialize(accessToken: _accessToken);

      setState(() => _status = 'Starting navigation...');
      final result = await _plugin.startNavigation(
        waypoints: const [_testDestination],
        options: NavigationOptions(simulateRoute: simulate),
        markers: [
          NavigationMarker(
            id: 'test-marker-1',
            latitude: 26.2050,
            longitude: -98.2250,
            icon: base64Decode(_markerIconBase64),
          ),
        ],
      );

      setState(() => _status = 'Result: ${result.name}');
    } on NavigationException catch (e) {
      setState(() => _status = 'Navigation error: ${e.code} - ${e.message}');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('mapbox_navigation_flutter_v3 example')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(_status, textAlign: TextAlign.center),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: _busy ? null : () => _startNavigation(simulate: false),
                  child: const Text('Start navigation (real GPS)'),
                ),
                const SizedBox(height: 12),
                ElevatedButton(
                  onPressed: _busy ? null : () => _startNavigation(simulate: true),
                  child: const Text('Start navigation (simulated route)'),
                ),
                if (_busy) ...[
                  const SizedBox(height: 24),
                  const CircularProgressIndicator(),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
