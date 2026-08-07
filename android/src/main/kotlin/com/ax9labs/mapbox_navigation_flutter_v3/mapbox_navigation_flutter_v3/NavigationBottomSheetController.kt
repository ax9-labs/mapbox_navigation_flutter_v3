package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateValue
import com.mapbox.navigation.ui.components.maneuver.view.MapboxTurnIconManeuver

/**
 * Owns the bottom trip sheet: the collapsed "trip summary" row (stop
 * button, large remaining duration, distance-bullet-arrival, a
 * context-sensitive action button) and the "detailed maneuver" row shown
 * when expanded, plus the arrival panel that replaces both on arrival.
 *
 * Pulled out of [NavigationActivity] specifically so this piece - the
 * newest, most novel part of the screen - has a single, testable seam
 * instead of living inline in an already-large Activity. [NavigationActivity]
 * still owns the SDK observers that produce the data this renders; this
 * class only renders it.
 */
internal class NavigationBottomSheetController(
    root: View,
    private val uiOptions: TurnByTurnUiOptions,
    theme: NavigationTheme,
    private val onStopClicked: () -> Unit,
    private val onActionClicked: () -> Unit
) {
    private val container: View = root.findViewById(R.id.mnfv3_bottomSheet)
    private val content: View = root.findViewById(R.id.mnfv3_bottomSheetContent)
    private val sheetHandle: View = root.findViewById(R.id.mnfv3_sheetHandle)
    private val divider: View = root.findViewById(R.id.mnfv3_sheetDivider)
    private val stopButton: ImageButton = root.findViewById(R.id.mnfv3_stopButton)
    private val actionButton: ImageButton = root.findViewById(R.id.mnfv3_tripActionButton)
    private val durationText: TextView = root.findViewById(R.id.mnfv3_remainingDurationText)
    private val distanceAndArrivalText: TextView = root.findViewById(R.id.mnfv3_distanceAndArrivalText)
    private val detailedManeuverRow: View = root.findViewById(R.id.mnfv3_detailedManeuverRow)
    private val detailedManeuverIcon: MapboxTurnIconManeuver = root.findViewById(R.id.mnfv3_detailedManeuverIcon)
    private val detailedManeuverText: TextView = root.findViewById(R.id.mnfv3_detailedManeuverText)
    private val detailedManeuverDistanceText: TextView = root.findViewById(R.id.mnfv3_detailedManeuverDistanceText)
    private val arrivalPanel: View = root.findViewById(R.id.mnfv3_arrivalPanel)

    private val behavior = BottomSheetBehavior.from(container)

    /** Height (px) of everything above the detailed maneuver row - the collapsed "peek" content. */
    val peekContentHeightPx: Int
        get() = sheetHandle.height + stopButton.height

    init {
        stopButton.setOnClickListener { onStopClicked() }
        actionButton.setOnClickListener { onActionClicked() }
        actionButton.isVisible = uiOptions.showRouteOverviewButton

        container.isVisible = uiOptions.showBottomSheet

        if (uiOptions.enableExpandableBottomSheet) {
            behavior.isHideable = false
            behavior.skipCollapsed = false
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            behavior.addBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(
                        bottomSheet: View,
                        newState: Int
                    ) {
                        content.contentDescription =
                            if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                                root.context.getString(R.string.mnfv3_collapse_trip_details)
                            } else {
                                root.context.getString(R.string.mnfv3_expand_trip_details)
                            }
                    }

                    override fun onSlide(
                        bottomSheet: View,
                        slideOffset: Float
                    ) {}
                }
            )
        } else {
            // No drag affordance to show, and nothing to collapse - both
            // rows are simply always visible.
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheetHandle.isVisible = false
        }

        applyTheme(theme)
    }

    private fun applyTheme(theme: NavigationTheme) {
        content.background = theme.cardBackground(theme.surfaceColor, topOnly = true)
        sheetHandle.setBackgroundColor(theme.sheetHandleColor)
        divider.setBackgroundColor(theme.dividerColor)
        durationText.setTextColor(theme.surfaceForegroundColor)
        distanceAndArrivalText.setTextColor(theme.secondaryTextColor)
        detailedManeuverText.setTextColor(theme.surfaceForegroundColor)
        detailedManeuverDistanceText.setTextColor(theme.secondaryTextColor)
        stopButton.background = theme.circularButtonBackground(stopButton.context, android.graphics.Color.TRANSPARENT)
        actionButton.background = theme.circularButtonBackground(actionButton.context, android.graphics.Color.TRANSPARENT)
    }

    /**
     * Populates the trip summary row from Mapbox's own trip-progress
     * formatter (locale/unit-aware already - see
     * [TripProgressUpdateValue.formatter]) rather than formatting
     * distance/time manually. Honors
     * [TurnByTurnUiOptions.showRemainingDuration]/[showRemainingDistance]/[showArrivalTime]
     * individually - the secondary line degrades gracefully if only one
     * of distance/arrival is enabled, and disappears entirely if neither is.
     */
    fun updateTripSummary(tripProgress: TripProgressUpdateValue) {
        if (uiOptions.showRemainingDuration) {
            durationText.isVisible = true
            durationText.text = tripProgress.formatter.getTimeRemaining(tripProgress.totalTimeRemaining)
        } else {
            durationText.isVisible = false
        }

        val distancePart = if (uiOptions.showRemainingDistance) tripProgress.formatter.getDistanceRemaining(tripProgress.distanceRemaining) else null
        val arrivalPart =
            if (uiOptions.showArrivalTime) {
                tripProgress.formatter.getEstimatedTimeToArrival(tripProgress.estimatedTimeToArrival, tripProgress.arrivalTimeZone)
            } else {
                null
            }
        distanceAndArrivalText.isVisible = distancePart != null || arrivalPart != null
        distanceAndArrivalText.text =
            when {
                distancePart != null && arrivalPart != null -> "$distancePart  •  $arrivalPart"
                distancePart != null -> distancePart
                arrivalPart != null -> arrivalPart
                else -> ""
            }
    }

    /**
     * Populates the detailed maneuver row (icon/instruction/distance) from
     * the same [Maneuver] data the top banner renders - reuses
     * [MapboxTurnIconManeuver], Mapbox's own reusable icon-rendering view,
     * rather than a custom icon resolver.
     */
    fun updateDetailedManeuver(maneuver: Maneuver?) {
        if (maneuver == null) {
            detailedManeuverRow.isVisible = false
            return
        }
        detailedManeuverRow.isVisible = true
        detailedManeuverIcon.renderPrimaryTurnIcon(maneuver.primary)
        detailedManeuverText.text = maneuver.primary.text
        val stepDistance = maneuver.stepDistance
        val remaining = stepDistance.distanceRemaining ?: stepDistance.totalDistance
        detailedManeuverDistanceText.text = stepDistance.distanceFormatter.formatDistance(remaining)

        detailedManeuverRow.contentDescription =
            detailedManeuverRow.context.getString(
                R.string.mnfv3_maneuver_announcement_template,
                maneuver.primary.text,
                detailedManeuverDistanceText.text
            )
    }

    fun showArrivalPanel() {
        content.isVisible = false
        arrivalPanel.isVisible = true
        if (uiOptions.enableExpandableBottomSheet) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }
}
