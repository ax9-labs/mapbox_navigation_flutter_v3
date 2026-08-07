package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.reroute.RerouteController
import com.mapbox.navigation.core.reroute.RerouteState
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer

/**
 * Hosts turn-by-turn navigation full-screen, composed from Mapbox Navigation
 * SDK v3's "standalone" building blocks (route request, route line
 * rendering, following camera, maneuver banner, trip progress, voice
 * instructions, custom marker annotations) rather than a turnkey Drop-In UI
 * screen - as of writing, Mapbox's own current example repo
 * (github.com/mapbox/mapbox-navigation-android-examples, `main` branch) no
 * longer demonstrates a Drop-In `NavigationView`, only these standalone
 * pieces, so this is built directly against APIs verified in that repo's
 * real, current example activities (FetchARouteActivity,
 * RenderRouteLineActivity, ShowCameraTransitionsActivity,
 * CustomArrivalActivity, ShowManeuversActivity, ShowTripProgressActivity,
 * PlayVoiceInstructionsActivity) and mapbox-maps-android's
 * PointAnnotationActivity.
 *
 * Intent/JSON parsing lives in [NavigationIntentCodec] (waypoints/options)
 * and [MarkerDecoder] (markers) rather than inline here, so that logic is
 * unit-testable independent of the Activity lifecycle.
 */
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class NavigationActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var maneuverView: MapboxManeuverView
    private lateinit var tripProgressView: MapboxTripProgressView
    private lateinit var rerouteView: TextView
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView

    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi
    private lateinit var speechApi: MapboxSpeechApi
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer

    private var pointAnnotationManager: PointAnnotationManager? = null

    private var arrived = false
    private var finishedWithResult = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val navigationLocationProvider = NavigationLocationProvider()

    /**
     * Drives simulated movement along the active route when
     * [NavigationStartOptions.simulateRoute] is set. Only self-sustains
     * once seeded with an initial location push (see
     * [startSimulatedTripSession]) - it schedules further simulated
     * positions off of each route-progress tick, but needs one real tick to
     * bootstrap. Real, verified pattern from Mapbox's own
     * CustomArrivalActivity / RenderRouteLineActivity examples -
     * `startReplayTripSession()` alone (what the first version of this file
     * did) configures replay mode but never actually feeds it any events,
     * so nothing moves.
     */
    private var replayProgressObserver: ReplayProgressObserver? = null

    private lateinit var destination: Point
    private lateinit var waypoints: List<Point>
    private lateinit var startOptions: NavigationStartOptions
    private lateinit var markers: List<NavigationMarkerData>

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

            if (startOptions.bannerInstructionsEnabled) {
                val maneuvers = maneuverApi.getManeuvers(routeProgress)
                maneuvers.onValue { maneuverList ->
                    maneuverView.isVisible = true
                    maneuverView.renderManeuvers(maneuvers)
                }
                maneuvers.onError { error -> Log.w(TAG, "Failed to compute maneuvers: ${error.errorMessage}") }
            }

            tripProgressView.isVisible = true
            tripProgressView.render(tripProgressApi.getTripProgress(routeProgress))

            // Arrival detection: mirrors the pattern Mapbox's own examples use
            // (CustomArrivalActivity) - there is no single "arrived" event,
            // so proximity to the destination plus distance-remaining is
            // treated as arrival.
            if (!arrived && routeProgress.distanceRemaining <= startOptions.arrivalDistanceMeters) {
                arrived = true
                Log.d(TAG, "Arrived: distanceRemaining=${routeProgress.distanceRemaining}m <= threshold=${startOptions.arrivalDistanceMeters}m")
                finishWithResult(RESULT_ARRIVED)
            }
        }

    private val voiceInstructionsObserver =
        VoiceInstructionsObserver { voiceInstructions ->
            speechApi.generate(voiceInstructions) { expected ->
                expected.fold(
                    { error -> voiceInstructionsPlayer.play(error.fallback) { speechApi.clean(error.fallback) } },
                    { value -> voiceInstructionsPlayer.play(value.announcement) { speechApi.clean(value.announcement) } }
                )
            }
        }

    /**
     * Rerouting itself is fully automatic - `MapboxNavigation`'s default
     * [com.mapbox.navigation.core.reroute.RerouteController] detects
     * off-route driving and requests+applies a new route on its own
     * (confirmed against Mapbox's own `TurnByTurnExperienceActivity`
     * example, whose `routesObserver` doc comment notes it fires for "a
     * reroute was executed" with no separate reroute-triggering code
     * anywhere in that example). This observer only adds the UI feedback
     * for that already-automatic process - a brief "Recalculating
     * route..." indicator - so the driver isn't looking at a stale route
     * line with no explanation for the few seconds a reroute takes.
     */
    private val rerouteStateObserver =
        RerouteController.RerouteStateObserver { state ->
            rerouteView.isVisible = state is RerouteState.FetchingRoute
            if (state is RerouteState.Failed) {
                Log.w(TAG, "Reroute failed: ${state.message}")
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
                maneuverView.isVisible = false
                tripProgressView.isVisible = false
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
                    if (startOptions.voiceInstructionsEnabled) {
                        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
                    }
                    mapboxNavigation.getRerouteController()?.registerRerouteStateObserver(rerouteStateObserver)
                    requestRoute(mapboxNavigation)
                }

                override fun onDetached(mapboxNavigation: MapboxNavigation) {
                    mapboxNavigation.unregisterLocationObserver(locationObserver)
                    mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                    mapboxNavigation.unregisterRoutesObserver(routesObserver)
                    if (startOptions.voiceInstructionsEnabled) {
                        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
                    }
                    mapboxNavigation.getRerouteController()?.unregisterRerouteStateObserver(rerouteStateObserver)
                }
            },
        onInitialize = this::initNavigation
    )

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accessToken = MapboxAccessToken.value
        val parsedWaypoints = NavigationIntentCodec.decodeWaypoints(intent.getStringExtra(EXTRA_WAYPOINTS))
        if (accessToken == null || parsedWaypoints.isEmpty()) {
            val code = if (accessToken == null) "NOT_INITIALIZED" else "NO_WAYPOINTS"
            val message =
                if (accessToken == null) {
                    "Mapbox access token not set - call initialize() before startNavigation()"
                } else {
                    // The Plugin already rejects an empty waypoints list before
                    // launching this Activity (NO_WAYPOINTS), so reaching this
                    // branch means waypoints were provided but all failed to
                    // decode - see NavigationIntentCodec.decodeWaypoints.
                    "No valid waypoints could be decoded from the launch Intent"
                }
            Log.e(TAG, "Cannot start navigation ($code): $message")
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_RESULT, RESULT_ERROR)
                    .putExtra(EXTRA_ERROR_CODE, code)
                    .putExtra(EXTRA_ERROR_MESSAGE, message)
            )
            finish()
            return
        }
        MapboxOptions.accessToken = accessToken
        waypoints = parsedWaypoints
        destination = parsedWaypoints.last()
        startOptions = NavigationIntentCodec.decodeOptions(intent.getStringExtra(EXTRA_OPTIONS))
        markers = PendingNavigationMarkers.consume()
        Log.d(TAG, "Starting navigation: ${waypoints.size} waypoint(s), ${markers.size} marker(s), options=$startOptions")

        setContentView(R.layout.mnfv3_activity_navigation)
        mapView = findViewById(R.id.mnfv3_mapView)
        maneuverView = findViewById(R.id.mnfv3_maneuverView)
        tripProgressView = findViewById(R.id.mnfv3_tripProgressView)
        rerouteView = findViewById(R.id.mnfv3_rerouteView)

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

        val distanceFormatterOptions = DistanceFormatterOptions.Builder(this).build()
        maneuverApi = MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions))
        tripProgressApi =
            MapboxTripProgressApi(TripProgressUpdateFormatter.Builder(this).build())
        speechApi = MapboxSpeechApi(this, startOptions.language)
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(this, startOptions.language)

        mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            puckBearingEnabled = true
            enabled = true
        }

        mapboxMap.loadStyle(Style.MAPBOX_STREETS) {
            renderMarkers()
        }

        // NOT `override fun onBackPressed()` - found by running this on a
        // real emulator (predictive-back-gesture Android build): with
        // predictive back enabled, the system routes back navigation
        // through OnBackInvokedCallback/OnBackPressedDispatcher and the
        // deprecated onBackPressed() override is never invoked at all, so
        // the Activity finished via the default system behavior (no
        // setResult() call), which the plugin then defaulted to
        // RESULT_ERROR instead of RESULT_CANCELLED. This is the
        // AndroidX-recommended replacement, compatible with both gesture
        // and legacy back navigation.
        onBackPressedDispatcher.addCallback(this) {
            finishWithResult(RESULT_CANCELLED)
        }
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
     * Renders [markers] as custom-bitmap point annotations - the reason
     * this plugin exists over a stock package, per the driving requirement:
     * incident/safe-zone markers rendered with the app's own icons, visible
     * on the map both before and during active turn-by-turn guidance (this
     * is called once from the style-load callback and the annotations
     * persist independently of route/trip-session state).
     */
    private fun renderMarkers() {
        if (markers.isEmpty()) return
        val manager = pointAnnotationManager ?: mapView.annotations.createPointAnnotationManager().also {
            pointAnnotationManager = it
        }
        manager.deleteAll()
        markers.forEach { marker ->
            manager.create(
                PointAnnotationOptions()
                    .withPoint(marker.point)
                    .withIconImage(marker.bitmap)
                    .withIconSize(marker.iconScale)
            )
        }
        Log.d(TAG, "Rendered ${markers.size} marker(s)")
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The Dart API promises navigation "from the device's current location
     * through waypoints" - callers only pass the destination(s), not an
     * origin. This resolves that origin with a single fresh location
     * request, falling back to the last-known-location cache only if a
     * fresh fix doesn't arrive within [LOCATION_TIMEOUT_MS].
     *
     * Originally this used only `LocationManager.getLastKnownLocation()`
     * across all providers - simpler, but the cache has no freshness bound
     * at all. We hit this directly during testing: a location fixed
     * earlier in an unrelated test session was still returned as "last
     * known" and used as the route origin, silently producing a wrong (or
     * trivially-already-arrived) route. A real navigation session should
     * not silently trust a fix that could be minutes or hours old.
     *
     * @param onResult Called with the resolved origin, or `null` plus a
     *   specific error code (`ERROR_LOCATION_PERMISSION_DENIED`,
     *   `ERROR_LOCATION_PROVIDER_DISABLED`, `ERROR_LOCATION_UNAVAILABLE`)
     *   when one can't be resolved - explicit permission checking here
     *   (rather than relying on `@SuppressLint("MissingPermission")` to
     *   paper over a potential `SecurityException`) means a missing
     *   permission surfaces as a clear, distinguishable
     *   [NavigationException] instead of an ambiguous generic failure.
     */
    private fun resolveOrigin(onResult: (Point?, String?) -> Unit) {
        if (!hasLocationPermission()) {
            onResult(null, ERROR_LOCATION_PERMISSION_DENIED)
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onResult(lastKnownLocationPoint(null), ERROR_LOCATION_UNAVAILABLE)
            return
        }
        val provider =
            when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
        if (provider == null) {
            Log.w(TAG, "No enabled location provider; falling back to last-known-location cache")
            val cached = lastKnownLocationPoint(locationManager)
            onResult(cached, if (cached == null) ERROR_LOCATION_PROVIDER_DISABLED else null)
            return
        }

        var resolved = false
        val listener =
            object : LocationListener {
                @SuppressLint("MissingPermission")
                override fun onLocationChanged(location: Location) {
                    if (resolved) return
                    resolved = true
                    mainHandler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
                    locationManager.removeUpdates(this)
                    Log.d(TAG, "Resolved fresh origin from $provider")
                    onResult(Point.fromLngLat(location.longitude, location.latitude), null)
                }
            }

        mainHandler.postAtTime(
            {
                if (resolved) return@postAtTime
                resolved = true
                locationManager.removeUpdates(listener)
                Log.w(TAG, "Fresh location request timed out after ${LOCATION_TIMEOUT_MS}ms; falling back to last-known-location cache")
                val cached = lastKnownLocationPoint(locationManager)
                onResult(cached, if (cached == null) ERROR_LOCATION_UNAVAILABLE else null)
            },
            TIMEOUT_TOKEN,
            android.os.SystemClock.uptimeMillis() + LOCATION_TIMEOUT_MS
        )

        @SuppressLint("MissingPermission") // hasLocationPermission() checked above
        val requestResult =
            runCatching {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        requestResult.onFailure {
            resolved = true
            mainHandler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            Log.w(TAG, "requestSingleUpdate failed, falling back to last-known-location cache: ${it.message}")
            val cached = lastKnownLocationPoint(locationManager)
            onResult(cached, if (cached == null) ERROR_LOCATION_UNAVAILABLE else null)
        }
    }

    /** Fallback only - see [resolveOrigin] for why a fresh fix is preferred. */
    @SuppressLint("MissingPermission") // callers only reach here after hasLocationPermission() passed
    private fun lastKnownLocationPoint(locationManager: LocationManager?): Point? {
        val manager = locationManager ?: (getSystemService(Context.LOCATION_SERVICE) as? LocationManager) ?: return null
        return manager.allProviders
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { Point.fromLngLat(it.longitude, it.latitude) }
    }

    private fun requestRoute(mapboxNavigation: MapboxNavigation) {
        resolveOrigin { origin, errorCode ->
            if (origin == null) {
                val code = errorCode ?: ERROR_LOCATION_UNAVAILABLE
                Log.e(TAG, "No location available to use as route origin ($code)")
                finishWithResult(RESULT_ERROR, code, locationErrorMessage(code))
                return@resolveOrigin
            }
            requestRouteFromOrigin(mapboxNavigation, origin)
        }
    }

    private fun locationErrorMessage(code: String): String =
        when (code) {
            ERROR_LOCATION_PERMISSION_DENIED ->
                "Location permission not granted - request ACCESS_FINE_LOCATION or " +
                    "ACCESS_COARSE_LOCATION before calling startNavigation()"
            ERROR_LOCATION_PROVIDER_DISABLED -> "No enabled location provider (GPS and network location are both off)"
            else -> "No location available to use as the route origin"
        }

    private fun requestRouteFromOrigin(
        mapboxNavigation: MapboxNavigation,
        origin: Point
    ) {
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
                    Log.d(TAG, "Route ready from $routerOrigin (${routes.size} route(s))")
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
                    val message = reasons.joinToString { it.message }.ifEmpty { "no route found" }
                    Log.e(TAG, "Route request failed: $message")
                    finishWithResult(RESULT_ERROR, "ROUTE_REQUEST_FAILED", message)
                }

                override fun onCanceled(
                    routeOptions: RouteOptions,
                    routerOrigin: String
                ) {
                    Log.w(TAG, "Route request canceled (routerOrigin=$routerOrigin)")
                    finishWithResult(RESULT_ERROR, "ROUTE_REQUEST_CANCELED", "Route request canceled (routerOrigin=$routerOrigin)")
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
            playbackSpeed(startOptions.simulateSpeedMultiplier)
            play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
        if (::routeLineApi.isInitialized) routeLineApi.cancel()
        if (::routeLineView.isInitialized) routeLineView.cancel()
        if (::maneuverApi.isInitialized) maneuverApi.cancel()
        if (::speechApi.isInitialized) speechApi.cancel()
        if (::voiceInstructionsPlayer.isInitialized) voiceInstructionsPlayer.shutdown()
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

    private fun finishWithResult(
        result: String,
        errorCode: String? = null,
        errorMessage: String? = null
    ) {
        if (finishedWithResult) return
        finishedWithResult = true
        val data =
            Intent().putExtra(EXTRA_RESULT, result).apply {
                if (errorCode != null) putExtra(EXTRA_ERROR_CODE, errorCode)
                if (errorMessage != null) putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
            }
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        private const val TAG = "NavigationActivity"

        const val EXTRA_RESULT = "com.ax9labs.mapbox_navigation_flutter_v3.RESULT"
        const val EXTRA_ERROR_CODE = "com.ax9labs.mapbox_navigation_flutter_v3.ERROR_CODE"
        const val EXTRA_ERROR_MESSAGE = "com.ax9labs.mapbox_navigation_flutter_v3.ERROR_MESSAGE"
        private const val EXTRA_WAYPOINTS = "com.ax9labs.mapbox_navigation_flutter_v3.WAYPOINTS"
        private const val EXTRA_OPTIONS = "com.ax9labs.mapbox_navigation_flutter_v3.OPTIONS"

        const val RESULT_ARRIVED = "arrived"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_ERROR = "error"

        const val ERROR_LOCATION_PERMISSION_DENIED = "LOCATION_PERMISSION_DENIED"
        const val ERROR_LOCATION_PROVIDER_DISABLED = "LOCATION_PROVIDER_DISABLED"
        const val ERROR_LOCATION_UNAVAILABLE = "LOCATION_UNAVAILABLE"

        private const val LOCATION_TIMEOUT_MS = 5_000L
        private const val TIMEOUT_TOKEN = "resolve-origin-timeout"

        /**
         * Markers are handed off via [PendingNavigationMarkers], not this
         * Intent, so callers must set that before invoking this. See
         * [PendingNavigationMarkers] for why.
         */
        fun newIntent(
            context: Context,
            waypoints: List<Map<String, Any?>>,
            options: Map<String, Any?>
        ): Intent =
            Intent(context, NavigationActivity::class.java)
                .putExtra(EXTRA_WAYPOINTS, NavigationIntentCodec.encodeWaypoints(waypoints))
                .putExtra(EXTRA_OPTIONS, NavigationIntentCodec.encodeOptions(options))
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
