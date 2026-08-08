import Flutter
import MapboxMaps
import UIKit

@MainActor
public class MapboxNavigationFlutterV3Plugin: NSObject, FlutterPlugin {
    private static var accessToken: String?
    private let coordinator = NavigationCoordinator()

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "mapbox_navigation_flutter_v3", binaryMessenger: registrar.messenger())
        let instance = MapboxNavigationFlutterV3Plugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getPlatformVersion":
            result("iOS " + UIDevice.current.systemVersion)
        case "initialize":
            handleInitialize(call, result: result)
        case "startNavigation":
            handleStartNavigation(call, result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func handleInitialize(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let accessToken = args["accessToken"] as? String, !accessToken.isEmpty
        else {
            result(FlutterError(code: "NO_TOKEN", message: "accessToken is required", details: nil))
            return
        }
        MapboxNavigationFlutterV3Plugin.accessToken = accessToken
        MapboxOptions.accessToken = accessToken
        result(nil)
    }

    private func handleStartNavigation(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard MapboxNavigationFlutterV3Plugin.accessToken != nil else {
            result(FlutterError(code: "NOT_INITIALIZED", message: "Call initialize() before startNavigation()", details: nil))
            return
        }
        guard let presentingViewController = UIApplication.shared.mnfv3TopViewController else {
            result(FlutterError(code: "NO_ACTIVITY", message: "startNavigation called with no attached view controller", details: nil))
            return
        }

        let args = call.arguments as? [String: Any]
        let rawWaypoints = args?["waypoints"] as? [[String: Any]]
        let waypoints = NavigationWaypointData.decodeList(rawWaypoints)
        guard !waypoints.isEmpty else {
            result(FlutterError(code: "NO_WAYPOINTS", message: "At least one waypoint is required", details: nil))
            return
        }

        let options = NavigationStartOptions.decode(args?["options"] as? [String: Any])
        let markers = MarkerDecoder.decodeMarkers(args?["markers"] as? [[String: Any]])

        coordinator.start(
            waypoints: waypoints,
            options: options,
            markers: markers,
            from: presentingViewController
        ) { outcome in
            switch outcome {
            case .success(let value):
                result(value)
            case .failure(let error):
                result(FlutterError(code: error.code, message: error.message, details: nil))
            }
        }
    }
}

private extension UIApplication {
    /// Finds the topmost presented view controller on the key window's
    /// root, so `startNavigation` can present `NavigationViewController`
    /// regardless of what screen the host Flutter app is currently
    /// showing - the iOS equivalent of Android's attached `Activity`.
    var mnfv3TopViewController: UIViewController? {
        let keyWindow = connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }

        var top = keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
