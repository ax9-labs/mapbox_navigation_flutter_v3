import CoreLocation
import Foundation

/// A single stop along the route, decoded from Dart's `NavigationWaypoint`.
struct NavigationWaypointData {
    let latitude: Double
    let longitude: Double
    let name: String?

    static func decode(_ raw: [String: Any]) -> NavigationWaypointData? {
        guard let latitude = raw["latitude"] as? Double,
              let longitude = raw["longitude"] as? Double
        else {
            return nil
        }
        return NavigationWaypointData(latitude: latitude, longitude: longitude, name: raw["name"] as? String)
    }

    static func decodeList(_ raw: [[String: Any]]?) -> [NavigationWaypointData] {
        guard let raw else { return [] }
        return raw.compactMap(decode)
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

/// Decoded, defaulted form of Dart's `NavigationOptions`. Unlike the
/// Android side (which round-trips this through a JSON string carried as
/// an Android `Intent` extra, because launching a separate `Activity`
/// requires it), the iOS plugin never leaves the same process/call, so
/// this decodes directly from the `[String: Any]` dictionary Flutter's
/// method channel already hands over - no JSON layer needed.
struct NavigationStartOptions {
    var profile: String = "driving-traffic"
    var language: String = "en"
    var voiceInstructionsEnabled = true
    var bannerInstructionsEnabled = true
    var simulateRoute = false
    var arrivalDistanceMeters: Double = 25
    var simulateSpeedMultiplier: Double = 3
    var theme: TurnByTurnTheme = .default
    var uiOptions: TurnByTurnUiOptions = .default

    static func decode(_ raw: [String: Any]?) -> NavigationStartOptions {
        var options = NavigationStartOptions()
        guard let raw else { return options }
        options.profile = raw["profile"] as? String ?? options.profile
        options.language = raw["language"] as? String ?? options.language
        options.voiceInstructionsEnabled = raw["voiceInstructionsEnabled"] as? Bool ?? options.voiceInstructionsEnabled
        options.bannerInstructionsEnabled = raw["bannerInstructionsEnabled"] as? Bool ?? options.bannerInstructionsEnabled
        options.simulateRoute = raw["simulateRoute"] as? Bool ?? options.simulateRoute
        options.arrivalDistanceMeters = raw["arrivalDistanceMeters"] as? Double ?? options.arrivalDistanceMeters
        options.simulateSpeedMultiplier = raw["simulateSpeedMultiplier"] as? Double ?? options.simulateSpeedMultiplier
        options.theme = TurnByTurnTheme.decode(raw["theme"] as? [String: Any])
        options.uiOptions = TurnByTurnUiOptions.decode(raw["uiOptions"] as? [String: Any])
        return options
    }
}
