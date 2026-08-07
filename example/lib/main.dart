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
