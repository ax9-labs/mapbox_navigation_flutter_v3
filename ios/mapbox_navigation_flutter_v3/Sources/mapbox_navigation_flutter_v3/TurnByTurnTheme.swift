import UIKit

/// Color/shape customization for the turn-by-turn UI, decoded from Dart's
/// `TurnByTurnTheme` (see `lib/src/models.dart`). Every field is optional -
/// `nil` means "use Mapbox's/this plugin's built-in default" rather than
/// every consumer having to specify a complete palette.
///
/// Colors arrive as packed ARGB ints (same representation as Flutter's
/// `Color.value`) - `uiColor` converts one to a `UIColor` when present.
struct TurnByTurnTheme {
    var primaryInstructionColor: Int?
    var primaryInstructionForegroundColor: Int?
    var secondaryInstructionColor: Int?
    var secondaryInstructionForegroundColor: Int?
    var surfaceColor: Int?
    var surfaceForegroundColor: Int?
    var routeColor: Int?
    var routeCasingColor: Int?
    var trafficLowColor: Int?
    var trafficModerateColor: Int?
    var trafficHeavyColor: Int?
    var trafficSevereColor: Int?
    var dividerColor: Int?
    var secondaryTextColor: Int?
    var maneuverIconColor: Int?
    var sheetHandleColor: Int?
    var cornerRadius: Double?
    var elevation: Double?

    static let `default` = TurnByTurnTheme()

    static func decode(_ json: [String: Any]?) -> TurnByTurnTheme {
        guard let json else { return .default }
        var theme = TurnByTurnTheme()
        theme.primaryInstructionColor = json["primaryInstructionColor"] as? Int
        theme.primaryInstructionForegroundColor = json["primaryInstructionForegroundColor"] as? Int
        theme.secondaryInstructionColor = json["secondaryInstructionColor"] as? Int
        theme.secondaryInstructionForegroundColor = json["secondaryInstructionForegroundColor"] as? Int
        theme.surfaceColor = json["surfaceColor"] as? Int
        theme.surfaceForegroundColor = json["surfaceForegroundColor"] as? Int
        theme.routeColor = json["routeColor"] as? Int
        theme.routeCasingColor = json["routeCasingColor"] as? Int
        theme.trafficLowColor = json["trafficLowColor"] as? Int
        theme.trafficModerateColor = json["trafficModerateColor"] as? Int
        theme.trafficHeavyColor = json["trafficHeavyColor"] as? Int
        theme.trafficSevereColor = json["trafficSevereColor"] as? Int
        theme.dividerColor = json["dividerColor"] as? Int
        theme.secondaryTextColor = json["secondaryTextColor"] as? Int
        theme.maneuverIconColor = json["maneuverIconColor"] as? Int
        theme.sheetHandleColor = json["sheetHandleColor"] as? Int
        theme.cornerRadius = json["cornerRadius"] as? Double
        theme.elevation = json["elevation"] as? Double
        return theme
    }
}

extension Int {
    /// Interprets this `Int` as a packed ARGB color (Flutter `Color.value`
    /// representation) and converts it to a `UIColor`.
    var asARGBColor: UIColor {
        let a = CGFloat((self >> 24) & 0xFF) / 255.0
        let r = CGFloat((self >> 16) & 0xFF) / 255.0
        let g = CGFloat((self >> 8) & 0xFF) / 255.0
        let b = CGFloat(self & 0xFF) / 255.0
        return UIColor(red: r, green: g, blue: b, alpha: a)
    }
}

/// Feature toggles for the turn-by-turn UI, decoded from Dart's
/// `TurnByTurnUiOptions`. Mirrors the Android `TurnByTurnUiOptions.kt`
/// decoder - see that file's doc comments for what each flag means.
struct TurnByTurnUiOptions {
    var showTopBanner = true
    var showNextManeuver = true
    var showBottomSheet = true
    var showTraffic = true
    var showTrafficSignals = false
    var showRoadLabels = true
    var showRecenterButton = true
    var showRouteOverviewButton = true
    var enableExpandableBottomSheet = true
    var confirmBeforeExitNavigation = false
    var showArrivalTime = true
    var showRemainingDistance = true
    var showRemainingDuration = true
    var enableNavigationCamera = true
    var enableManeuverAnimations = true

    static let `default` = TurnByTurnUiOptions()

    static func decode(_ json: [String: Any]?) -> TurnByTurnUiOptions {
        guard let json else { return .default }
        var options = TurnByTurnUiOptions()
        options.showTopBanner = json["showTopBanner"] as? Bool ?? options.showTopBanner
        options.showNextManeuver = json["showNextManeuver"] as? Bool ?? options.showNextManeuver
        options.showBottomSheet = json["showBottomSheet"] as? Bool ?? options.showBottomSheet
        options.showTraffic = json["showTraffic"] as? Bool ?? options.showTraffic
        options.showTrafficSignals = json["showTrafficSignals"] as? Bool ?? options.showTrafficSignals
        options.showRoadLabels = json["showRoadLabels"] as? Bool ?? options.showRoadLabels
        options.showRecenterButton = json["showRecenterButton"] as? Bool ?? options.showRecenterButton
        options.showRouteOverviewButton = json["showRouteOverviewButton"] as? Bool ?? options.showRouteOverviewButton
        options.enableExpandableBottomSheet = json["enableExpandableBottomSheet"] as? Bool ?? options.enableExpandableBottomSheet
        options.confirmBeforeExitNavigation = json["confirmBeforeExitNavigation"] as? Bool ?? options.confirmBeforeExitNavigation
        options.showArrivalTime = json["showArrivalTime"] as? Bool ?? options.showArrivalTime
        options.showRemainingDistance = json["showRemainingDistance"] as? Bool ?? options.showRemainingDistance
        options.showRemainingDuration = json["showRemainingDuration"] as? Bool ?? options.showRemainingDuration
        options.enableNavigationCamera = json["enableNavigationCamera"] as? Bool ?? options.enableNavigationCamera
        options.enableManeuverAnimations = json["enableManeuverAnimations"] as? Bool ?? options.enableManeuverAnimations
        return options
    }
}
