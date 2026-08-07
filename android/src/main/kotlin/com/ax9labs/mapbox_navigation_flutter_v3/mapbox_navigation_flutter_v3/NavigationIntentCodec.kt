package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.util.Log
import com.mapbox.geojson.Point
import org.json.JSONArray
import org.json.JSONObject

internal data class NavigationStartOptions(
    val profile: String,
    val language: String,
    val voiceInstructionsEnabled: Boolean,
    val bannerInstructionsEnabled: Boolean,
    val simulateRoute: Boolean,
    /** How close (meters) `distanceRemaining` must get before arrival fires. Tunable per profile - 25m default suits driving, but is likely too loose for `walking` and too tight on a highway. */
    val arrivalDistanceMeters: Double,
    /** Playback speed multiplier for [NavigationStartOptions.simulateRoute] sessions. Dev/QA convenience only - has no effect on real-GPS sessions. */
    val simulateSpeedMultiplier: Double,
    val theme: TurnByTurnTheme,
    val uiOptions: TurnByTurnUiOptions
) {
    companion object {
        val DEFAULT =
            NavigationStartOptions(
                profile = "driving-traffic",
                language = "en",
                voiceInstructionsEnabled = true,
                bannerInstructionsEnabled = true,
                simulateRoute = false,
                arrivalDistanceMeters = 25.0,
                simulateSpeedMultiplier = 3.0,
                theme = TurnByTurnTheme.DEFAULT,
                uiOptions = TurnByTurnUiOptions.DEFAULT
            )
    }
}

/**
 * Encodes/decodes the small (no image data) pieces of a `startNavigation`
 * call - waypoints and options - to/from the JSON strings carried as
 * launch-`Intent` extras. Markers are NOT part of this codec; see
 * [PendingNavigationMarkers] for why those use a different, same-process
 * transport instead of JSON-in-an-Intent-extra.
 *
 * Both decode directions are intentionally defensive: malformed input
 * degrades to an empty list / default options (logged) rather than
 * crashing `NavigationActivity.onCreate()`, matching the
 * never-crash-navigation-startup-over-recoverable-input policy used for
 * markers in [MarkerDecoder].
 */
internal object NavigationIntentCodec {
    private const val TAG = "NavigationIntentCodec"

    fun encodeWaypoints(waypoints: List<Map<String, Any?>>): String =
        JSONArray()
            .apply {
                waypoints.forEach { w ->
                    put(
                        JSONObject().apply {
                            put("latitude", w["latitude"])
                            put("longitude", w["longitude"])
                            put("name", w["name"])
                        }
                    )
                }
            }
            .toString()

    fun decodeWaypoints(json: String?): List<Point> {
        if (json == null) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Point.fromLngLat(obj.getDouble("longitude"), obj.getDouble("latitude"))
            }
        }.getOrElse {
            Log.w(TAG, "Failed to decode waypoints JSON, treating as empty: ${it.message}")
            emptyList()
        }
    }

    fun encodeOptions(options: Map<String, Any?>): String = JSONObject(options).toString()

    fun decodeOptions(json: String?): NavigationStartOptions {
        val defaults = NavigationStartOptions.DEFAULT
        if (json == null) return defaults
        return runCatching {
            val obj = JSONObject(json)
            NavigationStartOptions(
                profile = obj.optString("profile", defaults.profile),
                language = obj.optString("language", defaults.language),
                voiceInstructionsEnabled = obj.optBoolean("voiceInstructionsEnabled", defaults.voiceInstructionsEnabled),
                bannerInstructionsEnabled = obj.optBoolean("bannerInstructionsEnabled", defaults.bannerInstructionsEnabled),
                simulateRoute = obj.optBoolean("simulateRoute", defaults.simulateRoute),
                arrivalDistanceMeters = obj.optDouble("arrivalDistanceMeters", defaults.arrivalDistanceMeters),
                simulateSpeedMultiplier = obj.optDouble("simulateSpeedMultiplier", defaults.simulateSpeedMultiplier),
                theme = TurnByTurnTheme.decode(obj.optJSONObject("theme")),
                uiOptions = TurnByTurnUiOptions.decode(obj.optJSONObject("uiOptions"))
            )
        }.getOrElse {
            Log.w(TAG, "Failed to decode options JSON, falling back to defaults: ${it.message}")
            defaults
        }
    }
}
