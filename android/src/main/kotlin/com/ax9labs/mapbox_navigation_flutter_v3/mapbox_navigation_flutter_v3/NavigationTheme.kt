package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.core.content.ContextCompat
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources

/**
 * Resolves a (possibly-partial) [TurnByTurnTheme] against this plugin's
 * built-in defaults (`res/values/mnfv3_colors.xml`, `mnfv3_dimens.xml` -
 * and their `values-night` counterparts, so an unset field still respects
 * light/dark mode) into concrete colors/dimensions, and applies them to
 * the views that support runtime restyling.
 *
 * Every color/dimension read here goes through [TurnByTurnTheme]'s
 * nullable field first - resource defaults only apply when a caller
 * didn't override that specific value, per-field, not as an
 * all-or-nothing theme replacement.
 */
internal class NavigationTheme(context: Context, theme: TurnByTurnTheme) {
    val primaryInstructionColor = theme.primaryInstructionColor ?: color(context, R.color.mnfv3_primary_instruction_bg)
    val primaryInstructionForegroundColor =
        theme.primaryInstructionForegroundColor ?: color(context, R.color.mnfv3_primary_instruction_fg)
    val secondaryInstructionColor = theme.secondaryInstructionColor ?: color(context, R.color.mnfv3_secondary_instruction_bg)
    val secondaryInstructionForegroundColor =
        theme.secondaryInstructionForegroundColor ?: color(context, R.color.mnfv3_secondary_instruction_fg)
    val surfaceColor = theme.surfaceColor ?: color(context, R.color.mnfv3_surface)
    val surfaceForegroundColor = theme.surfaceForegroundColor ?: color(context, R.color.mnfv3_surface_fg)
    val routeColor = theme.routeColor ?: color(context, R.color.mnfv3_route_color)
    val routeCasingColor = theme.routeCasingColor ?: color(context, R.color.mnfv3_route_casing_color)
    val trafficLowColor = theme.trafficLowColor ?: color(context, R.color.mnfv3_traffic_low)
    val trafficModerateColor = theme.trafficModerateColor ?: color(context, R.color.mnfv3_traffic_moderate)
    val trafficHeavyColor = theme.trafficHeavyColor ?: color(context, R.color.mnfv3_traffic_heavy)
    val trafficSevereColor = theme.trafficSevereColor ?: color(context, R.color.mnfv3_traffic_severe)
    val dividerColor = theme.dividerColor ?: color(context, R.color.mnfv3_divider)
    val secondaryTextColor = theme.secondaryTextColor ?: color(context, R.color.mnfv3_secondary_text)
    val maneuverIconColor = theme.maneuverIconColor ?: color(context, R.color.mnfv3_maneuver_icon)
    val sheetHandleColor = theme.sheetHandleColor ?: color(context, R.color.mnfv3_sheet_handle)

    val cornerRadiusPx = dpToPx(context, theme.cornerRadius?.toFloat() ?: context.resources.getDimension(R.dimen.mnfv3_corner_radius) / context.resources.displayMetrics.density)
    val elevationPx = dpToPx(context, theme.elevation?.toFloat() ?: context.resources.getDimension(R.dimen.mnfv3_elevation) / context.resources.displayMetrics.density)

    /** A rounded, elevated card background (the top maneuver banner, the bottom sheet). */
    fun cardBackground(color: Int, topOnly: Boolean = false, bottomOnly: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadii =
                when {
                    topOnly -> floatArrayOf(cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, 0f, 0f, 0f, 0f)
                    bottomOnly -> floatArrayOf(0f, 0f, 0f, 0f, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx)
                    else -> FloatArray(8) { cornerRadiusPx }
                }
        }

    /** A circular, ripple-enabled background for icon buttons (stop/recenter/overview). */
    fun circularButtonBackground(
        context: Context,
        backgroundColor: Int
    ): Drawable {
        val base = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(backgroundColor) }
        val ripple = ContextCompat.getColor(context, android.R.color.darker_gray)
        return RippleDrawable(ColorStateList.valueOf(ripple), base, base)
    }

    /**
     * Traffic-aware route line colors, sourced from this theme's
     * `traffic*Color` fields. Congestion coloring itself is Mapbox's own
     * route line rendering (already fed `congestion_numeric` route
     * annotations via `applyDefaultNavigationOptions()`); this only
     * supplies which colors to use for each congestion level.
     */
    fun routeLineColorResources(): RouteLineColorResources =
        RouteLineColorResources.Builder()
            .routeDefaultColor(routeColor)
            .routeCasingColor(routeCasingColor)
            .routeLowCongestionColor(trafficLowColor)
            .routeModerateCongestionColor(trafficModerateColor)
            .routeHeavyCongestionColor(trafficHeavyColor)
            .routeSevereCongestionColor(trafficSevereColor)
            .build()

    /**
     * Deliberately does NOT call `MapboxManeuverView.updateManeuverViewOptions(...)`
     * for background colors - found only by running it: despite the
     * builder's `maneuverBackgroundColor(Int)` signature looking like a
     * plain ARGB setter, the SDK internally treats that `Int` as a
     * `@ColorRes` resource ID and calls `ContextCompat.getColor(context,
     * value)` on it, which throws `Resources.NotFoundException` for any
     * arbitrary runtime ARGB value (exactly what a Dart-configurable theme
     * produces). Since [MapboxManeuverView] is always wrapped in our own
     * themed card (see `NavigationActivity.setupTopOverlay`), the simpler
     * and crash-proof fix is to make the view's own background transparent
     * via the plain `View` API and let our card color show through
     * instead.
     */
    fun applyToManeuverView(view: com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView) {
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    companion object {
        private fun color(
            context: Context,
            resId: Int
        ): Int = ContextCompat.getColor(context, resId)

        private fun dpToPx(
            context: Context,
            dp: Float
        ): Float = dp * context.resources.displayMetrics.density
    }
}
