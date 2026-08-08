import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

/// Applies a [TurnByTurnTheme] to `NavigationViewController`'s UI via
/// Mapbox's `UIAppearance`-proxy styling API (`DayStyle`/`NightStyle`
/// subclassing) - the same mechanism Mapbox's own examples use (see
/// `Styled-UI-Elements.swift` in mapbox-navigation-ios's example app).
///
/// Unlike Android (where we built every UI piece ourselves and applied
/// colors directly), iOS's `NavigationViewController` is Mapbox's own
/// turnkey drop-in UI - there is no custom view hierarchy to theme here,
/// only Mapbox's existing components via their appearance-proxy API. A
/// field-by-field mapping table is in TURN_BY_TURN_UI_REDESIGN_IOS.md.
enum NavigationThemeFactory {
    static func makeStyles(theme: TurnByTurnTheme) -> [Style] {
        [ThemedDayStyle(theme: theme), ThemedNightStyle(theme: theme)]
    }

    /// Congestion colors for the route line - the direct iOS equivalent of
    /// Android's `RouteLineColorResources`, applied to
    /// `NavigationMapView.congestionConfiguration` rather than through the
    /// appearance-proxy system (congestion coloring isn't a `UIAppearance`
    /// property).
    static func congestionConfiguration(theme: TurnByTurnTheme) -> CongestionConfiguration {
        let defaults = CongestionColorsConfiguration.Colors.defaultMainRouteColors
        let colors = CongestionColorsConfiguration.Colors(
            low: theme.trafficLowColor?.asARGBColor ?? defaults.low,
            moderate: theme.trafficModerateColor?.asARGBColor ?? defaults.moderate,
            heavy: theme.trafficHeavyColor?.asARGBColor ?? defaults.heavy,
            severe: theme.trafficSevereColor?.asARGBColor ?? defaults.severe,
            unknown: defaults.unknown
        )
        return CongestionConfiguration(
            colors: .init(mainRouteColors: colors, alternativeRouteColors: colors),
            ranges: .default
        )
    }

    /// Applies the shared appearance-proxy customizations common to both
    /// day and night styles - only the fields the theme actually
    /// overrides are touched, everything else keeps Mapbox's own
    /// day/night default (mirrors `TurnByTurnTheme`'s
    /// override-only-what-you-set design on Android).
    static func applyAppearance(theme: TurnByTurnTheme, traitCollection: UITraitCollection) {
        if let color = theme.primaryInstructionColor?.asARGBColor {
            TopBannerView.appearance(for: traitCollection).backgroundColor = color
            InstructionsBannerView.appearance(for: traitCollection).backgroundColor = color
            ManeuverView.appearance(for: traitCollection).backgroundColor = color
        }
        if let color = theme.primaryInstructionForegroundColor?.asARGBColor {
            PrimaryLabel.appearance(for: traitCollection, whenContainedInInstancesOf: [InstructionsBannerView.self])
                .normalTextColor = color
            DistanceLabel.appearance(for: traitCollection, whenContainedInInstancesOf: [InstructionsBannerView.self])
                .valueTextColor = color
        }
        if let color = theme.secondaryInstructionColor?.asARGBColor {
            NextBannerView.appearance(for: traitCollection).backgroundColor = color
        }
        if let color = theme.secondaryInstructionForegroundColor?.asARGBColor {
            NextInstructionLabel.appearance(for: traitCollection).textColor = color
            SecondaryLabel.appearance(for: traitCollection, whenContainedInInstancesOf: [InstructionsBannerView.self])
                .normalTextColor = color
        }
        if let color = theme.surfaceColor?.asARGBColor {
            BottomBannerView.appearance(for: traitCollection).backgroundColor = color
            BottomPaddingView.appearance(for: traitCollection).backgroundColor = color
        }
        if let color = theme.surfaceForegroundColor?.asARGBColor {
            DistanceRemainingLabel.appearance(for: traitCollection).textColor = color
            TimeRemainingLabel.appearance(for: traitCollection).textColor = color
        }
        if let color = theme.routeColor?.asARGBColor {
            NavigationMapView.appearance(for: traitCollection).tintColor = color
        }
        if let color = theme.secondaryTextColor?.asARGBColor {
            ArrivalTimeLabel.appearance(for: traitCollection).textColor = color
        }
        if let color = theme.maneuverIconColor?.asARGBColor {
            ManeuverView.appearance(for: traitCollection, whenContainedInInstancesOf: [InstructionsBannerView.self])
                .primaryColor = color
        }
    }
}

private final class ThemedDayStyle: DayStyle {
    private let theme: TurnByTurnTheme

    init(theme: TurnByTurnTheme) {
        self.theme = theme
        super.init()
        styleType = .day
    }

    required init() {
        theme = .default
        super.init()
        styleType = .day
    }

    override func apply() {
        super.apply()
        NavigationThemeFactory.applyAppearance(theme: theme, traitCollection: UIScreen.main.traitCollection)
    }
}

private final class ThemedNightStyle: NightStyle {
    private let theme: TurnByTurnTheme

    init(theme: TurnByTurnTheme) {
        self.theme = theme
        super.init()
        styleType = .night
    }

    required init() {
        theme = .default
        super.init()
        styleType = .night
    }

    override func apply() {
        super.apply()
        NavigationThemeFactory.applyAppearance(theme: theme, traitCollection: UIScreen.main.traitCollection)
    }
}
