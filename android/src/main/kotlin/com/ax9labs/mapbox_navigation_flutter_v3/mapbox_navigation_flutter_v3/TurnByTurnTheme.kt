package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import org.json.JSONObject

/**
 * Color/shape customization for the turn-by-turn UI, decoded from Dart's
 * `TurnByTurnTheme` (see `lib/src/models.dart`). Every field is nullable -
 * `null` means "use [NavigationTheme.resolveDefaults]'s built-in default"
 * rather than every consumer having to specify a complete palette.
 *
 * Colors are packed ARGB ints - the same representation as Flutter's
 * `Color.value`, so no conversion is needed on either side of the channel.
 */
internal data class TurnByTurnTheme(
    val primaryInstructionColor: Int?,
    val primaryInstructionForegroundColor: Int?,
    val secondaryInstructionColor: Int?,
    val secondaryInstructionForegroundColor: Int?,
    val surfaceColor: Int?,
    val surfaceForegroundColor: Int?,
    val routeColor: Int?,
    val routeCasingColor: Int?,
    val trafficLowColor: Int?,
    val trafficModerateColor: Int?,
    val trafficHeavyColor: Int?,
    val trafficSevereColor: Int?,
    val dividerColor: Int?,
    val secondaryTextColor: Int?,
    val maneuverIconColor: Int?,
    val sheetHandleColor: Int?,
    val cornerRadius: Double?,
    val elevation: Double?
) {
    companion object {
        val DEFAULT =
            TurnByTurnTheme(
                primaryInstructionColor = null,
                primaryInstructionForegroundColor = null,
                secondaryInstructionColor = null,
                secondaryInstructionForegroundColor = null,
                surfaceColor = null,
                surfaceForegroundColor = null,
                routeColor = null,
                routeCasingColor = null,
                trafficLowColor = null,
                trafficModerateColor = null,
                trafficHeavyColor = null,
                trafficSevereColor = null,
                dividerColor = null,
                secondaryTextColor = null,
                maneuverIconColor = null,
                sheetHandleColor = null,
                cornerRadius = null,
                elevation = null
            )

        fun decode(json: JSONObject?): TurnByTurnTheme {
            if (json == null) return DEFAULT
            fun color(key: String): Int? = if (json.has(key) && !json.isNull(key)) json.optInt(key) else null
            fun dimen(key: String): Double? = if (json.has(key) && !json.isNull(key)) json.optDouble(key) else null
            return TurnByTurnTheme(
                primaryInstructionColor = color("primaryInstructionColor"),
                primaryInstructionForegroundColor = color("primaryInstructionForegroundColor"),
                secondaryInstructionColor = color("secondaryInstructionColor"),
                secondaryInstructionForegroundColor = color("secondaryInstructionForegroundColor"),
                surfaceColor = color("surfaceColor"),
                surfaceForegroundColor = color("surfaceForegroundColor"),
                routeColor = color("routeColor"),
                routeCasingColor = color("routeCasingColor"),
                trafficLowColor = color("trafficLowColor"),
                trafficModerateColor = color("trafficModerateColor"),
                trafficHeavyColor = color("trafficHeavyColor"),
                trafficSevereColor = color("trafficSevereColor"),
                dividerColor = color("dividerColor"),
                secondaryTextColor = color("secondaryTextColor"),
                maneuverIconColor = color("maneuverIconColor"),
                sheetHandleColor = color("sheetHandleColor"),
                cornerRadius = dimen("cornerRadius"),
                elevation = dimen("elevation")
            )
        }
    }
}

/**
 * Feature toggles for the turn-by-turn UI, decoded from Dart's
 * `TurnByTurnUiOptions`. See that class's doc comments for what each flag
 * means - kept in sync manually since these mirror each other across the
 * platform channel rather than being generated.
 */
internal data class TurnByTurnUiOptions(
    val showTopBanner: Boolean,
    val showNextManeuver: Boolean,
    val showBottomSheet: Boolean,
    val showTraffic: Boolean,
    val showTrafficSignals: Boolean,
    val showRoadLabels: Boolean,
    val showRecenterButton: Boolean,
    val showRouteOverviewButton: Boolean,
    val enableExpandableBottomSheet: Boolean,
    val confirmBeforeExitNavigation: Boolean,
    val showArrivalTime: Boolean,
    val showRemainingDistance: Boolean,
    val showRemainingDuration: Boolean,
    val enableNavigationCamera: Boolean,
    val enableManeuverAnimations: Boolean
) {
    companion object {
        val DEFAULT =
            TurnByTurnUiOptions(
                showTopBanner = true,
                showNextManeuver = true,
                showBottomSheet = true,
                showTraffic = true,
                showTrafficSignals = false,
                showRoadLabels = true,
                showRecenterButton = true,
                showRouteOverviewButton = true,
                enableExpandableBottomSheet = true,
                confirmBeforeExitNavigation = false,
                showArrivalTime = true,
                showRemainingDistance = true,
                showRemainingDuration = true,
                enableNavigationCamera = true,
                enableManeuverAnimations = true
            )

        fun decode(json: JSONObject?): TurnByTurnUiOptions {
            if (json == null) return DEFAULT
            val d = DEFAULT
            return TurnByTurnUiOptions(
                showTopBanner = json.optBoolean("showTopBanner", d.showTopBanner),
                showNextManeuver = json.optBoolean("showNextManeuver", d.showNextManeuver),
                showBottomSheet = json.optBoolean("showBottomSheet", d.showBottomSheet),
                showTraffic = json.optBoolean("showTraffic", d.showTraffic),
                showTrafficSignals = json.optBoolean("showTrafficSignals", d.showTrafficSignals),
                showRoadLabels = json.optBoolean("showRoadLabels", d.showRoadLabels),
                showRecenterButton = json.optBoolean("showRecenterButton", d.showRecenterButton),
                showRouteOverviewButton = json.optBoolean("showRouteOverviewButton", d.showRouteOverviewButton),
                enableExpandableBottomSheet = json.optBoolean("enableExpandableBottomSheet", d.enableExpandableBottomSheet),
                confirmBeforeExitNavigation = json.optBoolean("confirmBeforeExitNavigation", d.confirmBeforeExitNavigation),
                showArrivalTime = json.optBoolean("showArrivalTime", d.showArrivalTime),
                showRemainingDistance = json.optBoolean("showRemainingDistance", d.showRemainingDistance),
                showRemainingDuration = json.optBoolean("showRemainingDuration", d.showRemainingDuration),
                enableNavigationCamera = json.optBoolean("enableNavigationCamera", d.enableNavigationCamera),
                enableManeuverAnimations = json.optBoolean("enableManeuverAnimations", d.enableManeuverAnimations)
            )
        }
    }
}
