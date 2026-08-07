package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hosts turn-by-turn navigation full-screen, composed from Mapbox Navigation
 * SDK v3's "standalone" building blocks (route request, route line
 * rendering, following camera) rather than a turnkey Drop-In UI screen -
 * as of writing, Mapbox's own current example repo
 * (github.com/mapbox/mapbox-navigation-android-examples, `main` branch)
 * no longer demonstrates a Drop-In `NavigationView`, only these standalone
 * pieces, so this is built directly against APIs verified in that repo's
 * real, current example activities (FetchARouteActivity,
 * RenderRouteLineActivity, ShowCameraTransitionsActivity,
 * CustomArrivalActivity).
 *
 * MVP scope: route request, route line on the map, a following camera, and
 * progress-based arrival detection. NOT yet included (left as follow-up,
 * see README): maneuver banner (MapboxManeuverApi/View), trip progress
 * (MapboxTripProgressApi/View), speed limit badge, and voice instructions
 * (MapboxVoiceInstructionsPlayer) - each is a real, separate standalone
 * component in the same SDK, not fundamentally different work, just not
 * wired up yet.
 */
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class NavigationActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView

    private var arrived = false
    private var finishedWithResult = false

    private val navigationLocationProvider = NavigationLocationProvider()

    /**
     * Drives simulated movement along the active route when
     * [NavigationStartOptions.simulateRoute] is set. Only self-sustains
     * once seeded with an initial location push (see
     * [seedReplayAtOrigin]) - it schedules further simulated positions off
     * of each route-progress tick, so it needs one real tick to bootstrap.
     * Real, verified pattern from Mapbox's own CustomArrivalActivity /
     * RenderRouteLineActivity examples - `startReplayTripSession()` alone
     * (what the first version of this file did) configures replay mode
     * but never actually feeds it any events, so nothing moves.
     */
    private var replayProgressObserver: ReplayProgressObserver? = null

    private lateinit var destination: Point
    private lateinit var waypoints: List<Point>
    private lateinit var startOptions: NavigationStartOptions

    private val locationObserver =
        object : LocationObserver {
            var firstLocationUpdateReceived = false

            override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {}

            override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
                val enhancedLocation = locationMatcherResult.enhancedLocation
                navigationLocationProvider.changePosition(
                    location = enhancedLocation,
                    keyPoints = locationMatcherResult.keyPoints
                )
                viewportDataSource.onLocationChanged(enhancedLocation)
                viewportDataSource.evaluate()

                if (!firstLocationUpdateReceived) {
                    firstLocationUpdateReceived = true
                    navigationCamera.requestNavigationCameraToFollowing()
                }
            }
        }

    private val routeProgressObserver =
        RouteProgressObserver { routeProgress ->
            viewportDataSource.onRouteProgressChanged(routeProgress)
            viewportDataSource.evaluate()

            // Arrival detection: mirrors the pattern Mapbox's own examples use
            // (CustomArrivalActivity) - there is no single "arrived" event,
            // so proximity to the destination plus distance-remaining is
            // treated as arrival.
            if (!arrived && routeProgress.distanceRemaining <= ARRIVAL_DISTANCE_THRESHOLD_METERS) {
                arrived = true
                finishWithResult(RESULT_ARRIVED)
            }
        }

    private val routesObserver =
        RoutesObserver { routeUpdateResult ->
            if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
                routeLineApi.setNavigationRoutes(
                    routeUpdateResult.navigationRoutes
                ) { value ->
                    mapView.mapboxMap.style?.let { style ->
                        routeLineView.renderRouteDrawData(style, value)
                    }
                }
                viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
                viewportDataSource.evaluate()
            } else {
                routeLineApi.clearRouteLine { value ->
                    mapView.mapboxMap.style?.let { style ->
                        routeLineView.renderClearRouteLineValue(style, value)
                    }
                }
                viewportDataSource.clearRouteData()
                viewportDataSource.evaluate()
            }
        }

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver =
            object : MapboxNavigationObserver {
                @SuppressLint("MissingPermission")
                override fun onAttached(mapboxNavigation: MapboxNavigation) {
                    mapboxNavigation.registerLocationObserver(locationObserver)
                    mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
                    mapboxNavigation.registerRoutesObserver(routesObserver)
                    requestRoute(mapboxNavigation)
                }

                override fun onDetached(mapboxNavigation: MapboxNavigation) {
                    mapboxNavigation.unregisterLocationObserver(locationObserver)
                    mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                    mapboxNavigation.unregisterRoutesObserver(routesObserver)
                }
            },
        onInitialize = this::initNavigation
    )

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accessToken = MapboxAccessToken.value
        val parsedWaypoints = parseWaypoints(intent)
        if (accessToken == null || parsedWaypoints.isEmpty()) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, RESULT_ERROR))
            finish()
            return
        }
        MapboxOptions.accessToken = accessToken
        waypoints = parsedWaypoints
        destination = parsedWaypoints.last()
        startOptions = parseOptions(intent)

        setContentView(R.layout.mnfv3_activity_navigation)
        mapView = findViewById(R.id.mnfv3_mapView)

        val mapboxMap = mapView.mapboxMap
        viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
        navigationCamera =
            NavigationCamera(
                mapboxMap,
                mapView.camera,
                viewportDataSource
            )
        mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )

        routeLineView =
            MapboxRouteLineView(
                MapboxRouteLineViewOptions.Builder(this)
                    .routeLineBelowLayerId("road-label")
                    .build()
            )
        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            puckBearingEnabled = true
            enabled = true
        }

        mapboxMap.loadStyle(Style.MAPBOX_STREETS) {}
    }

    override fun onStart() {
        super.onStart()
        // First real access to the `by requireMapboxNavigation(...)` delegate
        // (onInitialize then onAttached), which kicks off the actual route
        // request. Must happen no earlier than onStart() - triggering it
        // synchronously inside onCreate() throws IllegalStateException
        // ("attached lifecycle is at least CREATED") because the Activity's
        // lifecycle registry hasn't finished transitioning to CREATED yet
        // while onCreate() itself is still executing. Mapbox's own examples
        // only ever touch this property from a later event (a button
        // click); this is the equivalent for a no-user-interaction launch.
        mapboxNavigation
    }

    private fun initNavigation() {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this).build()
        )
    }

    /**
     * The Dart API promises navigation "from the device's current location
     * through waypoints" - callers only pass the destination(s), not an
     * origin. This does the one-shot current-location fetch that makes
     * that true. (First real build surfaced this as a live bug: without
     * it, `coordinatesList(waypoints)` sent a single-point request and
     * Mapbox's Directions API correctly rejected it with "minimum number
     * of coordinates is 2".)
     */
    @SuppressLint("MissingPermission")
    private fun lastKnownLocationPoint(): Point? {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return locationManager.allProviders
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { Point.fromLngLat(it.longitude, it.latitude) }
    }

    private fun requestRoute(mapboxNavigation: MapboxNavigation) {
        val origin = lastKnownLocationPoint()
        if (origin == null) {
            finishWithResult(RESULT_ERROR)
            return
        }

        val routeOptions =
            RouteOptions
                .builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .coordinatesList(listOf(origin) + waypoints)
                .profile(startOptions.profile)
                .language(startOptions.language)
                .alternatives(false)
                .build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: String
                ) {
                    mapboxNavigation.setNavigationRoutes(routes)
                    if (startOptions.simulateRoute) {
                        startSimulatedTripSession(mapboxNavigation, origin)
                    } else {
                        mapboxNavigation.startTripSession()
                    }
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    finishWithResult(RESULT_ERROR)
                }

                override fun onCanceled(
                    routeOptions: RouteOptions,
                    routerOrigin: String
                ) {
                    finishWithResult(RESULT_ERROR)
                }
            }
        )
    }

    /**
     * `startReplayTripSession()` alone only configures replay mode - it
     * doesn't feed the replayer any events, so nothing actually moves
     * without this. [ReplayProgressObserver] self-sustains simulated
     * movement along the active route off of each route-progress tick,
     * but needs one real tick to bootstrap from, which is what pushing a
     * single location-at-origin event does here.
     */
    private fun startSimulatedTripSession(
        mapboxNavigation: MapboxNavigation,
        origin: Point
    ) {
        val observer = ReplayProgressObserver(mapboxNavigation.mapboxReplayer)
        replayProgressObserver = observer
        mapboxNavigation.registerRouteProgressObserver(observer)

        mapboxNavigation.startReplayTripSession()
        with(mapboxNavigation.mapboxReplayer) {
            pushEvents(
                listOf(ReplayRouteMapper.mapToUpdateLocation(System.currentTimeMillis().toDouble(), origin))
            )
            playFirstLocation()
            playbackSpeed(3.0)
            play()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithResult(RESULT_CANCELLED)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::routeLineApi.isInitialized) routeLineApi.cancel()
        if (::routeLineView.isInitialized) routeLineView.cancel()
        // Guards against the early-return path in onCreate() (missing
        // token/waypoints), where onStart() never ran and the
        // `by requireMapboxNavigation(...)` delegate was never triggered -
        // accessing it here in that case would throw the same
        // IllegalStateException fixed elsewhere in this file.
        if (::startOptions.isInitialized && MapboxNavigationApp.current() != null) {
            replayProgressObserver?.let { mapboxNavigation.unregisterRouteProgressObserver(it) }
            if (startOptions.simulateRoute) mapboxNavigation.mapboxReplayer.finish()
        }
    }

    private fun finishWithResult(result: String) {
        if (finishedWithResult) return
        finishedWithResult = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, result))
        finish()
    }

    private data class NavigationStartOptions(
        val profile: String,
        val language: String,
        val voiceInstructionsEnabled: Boolean,
        val bannerInstructionsEnabled: Boolean,
        val simulateRoute: Boolean
    )

    private fun parseWaypoints(intent: Intent): List<Point> {
        val json = intent.getStringExtra(EXTRA_WAYPOINTS) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Point.fromLngLat(obj.getDouble("longitude"), obj.getDouble("latitude"))
        }
    }

    private fun parseOptions(intent: Intent): NavigationStartOptions {
        val json = intent.getStringExtra(EXTRA_OPTIONS)
        val obj = if (json != null) JSONObject(json) else JSONObject()
        return NavigationStartOptions(
            profile = obj.optString("profile", "driving-traffic"),
            language = obj.optString("language", "en"),
            voiceInstructionsEnabled = obj.optBoolean("voiceInstructionsEnabled", true),
            bannerInstructionsEnabled = obj.optBoolean("bannerInstructionsEnabled", true),
            simulateRoute = obj.optBoolean("simulateRoute", false)
        )
    }

    companion object {
        const val EXTRA_RESULT = "com.ax9labs.mapbox_navigation_flutter_v3.RESULT"
        private const val EXTRA_WAYPOINTS = "com.ax9labs.mapbox_navigation_flutter_v3.WAYPOINTS"
        private const val EXTRA_OPTIONS = "com.ax9labs.mapbox_navigation_flutter_v3.OPTIONS"

        const val RESULT_ARRIVED = "arrived"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_ERROR = "error"

        private const val ARRIVAL_DISTANCE_THRESHOLD_METERS = 25.0

        fun newIntent(
            context: Context,
            waypoints: List<Map<String, Any?>>,
            options: Map<String, Any?>
        ): Intent {
            val waypointsJson =
                JSONArray().apply {
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
            val optionsJson = JSONObject(options)
            return Intent(context, NavigationActivity::class.java)
                .putExtra(EXTRA_WAYPOINTS, waypointsJson.toString())
                .putExtra(EXTRA_OPTIONS, optionsJson.toString())
        }
    }
}

/**
 * Process-wide holder for the Mapbox public access token set via
 * [MapboxNavigationFlutterV3Plugin.initialize]. [NavigationActivity] reads
 * this before configuring the Mapbox SDK - there's no other clean way to
 * hand a value from the plugin's MethodChannel handler to an Activity
 * launched via Intent without round-tripping a secret through Intent
 * extras (visible to other apps via adb) on every navigation start.
 */
internal object MapboxAccessToken {
    var value: String? = null
}
