import Flutter
import UIKit

/// NOTE: iOS native navigation UI is not implemented yet (Android was
/// built first and already turned out to be a much bigger scope than
/// originally estimated - see README.md). `initialize` is real; `startNavigation`
/// currently returns a clear NOT_IMPLEMENTED error rather than silently
/// doing nothing, so callers fail loudly instead of hanging.
public class MapboxNavigationFlutterV3Plugin: NSObject, FlutterPlugin {
  private static var accessToken: String?

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
      guard let args = call.arguments as? [String: Any],
        let accessToken = args["accessToken"] as? String, !accessToken.isEmpty
      else {
        result(FlutterError(code: "NO_TOKEN", message: "accessToken is required", details: nil))
        return
      }
      MapboxNavigationFlutterV3Plugin.accessToken = accessToken
      result(nil)
    case "startNavigation":
      result(
        FlutterError(
          code: "NOT_IMPLEMENTED",
          message: "iOS native navigation UI has not been built yet. See README.md.",
          details: nil
        ))
    default:
      result(FlutterMethodNotImplemented)
    }
  }
}
