package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
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
import org.json.JSONArray
import org.json.JSONObject

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
 */
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class NavigationActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var maneuverView: MapboxManeuverView
    private lateinit var tripProgressView: MapboxTripProgressView
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
            }

            tripProgressView.isVisible = true
            tripProgressView.render(tripProgressApi.getTripProgress(routeProgress))

            // Arrival detection: mirrors the pattern Mapbox's own examples use
            // (CustomArrivalActivity) - there is no single "arrived" event,
            // so proximity to the destination plus distance-remaining is
            // treated as arrival.
            if (!arrived && routeProgress.distanceRemaining <= ARRIVAL_DISTANCE_THRESHOLD_METERS) {
                arrived = true
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
                    requestRoute(mapboxNavigation)
                }

                override fun onDetached(mapboxNavigation: MapboxNavigation) {
                    mapboxNavigation.unregisterLocationObserver(locationObserver)
                    mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                    mapboxNavigation.unregisterRoutesObserver(routesObserver)
                    mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
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
        markers = parseMarkers(intent)

        setContentView(R.layout.mnfv3_activity_navigation)
        mapView = findViewById(R.id.mnfv3_mapView)
        maneuverView = findViewById(R.id.mnfv3_maneuverView)
        tripProgressView = findViewById(R.id.mnfv3_tripProgressView)

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

    private data class NavigationMarkerData(
        val id: String,
        val point: Point,
        val bitmap: Bitmap,
        val iconScale: Double
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

    /**
     * Marker icons cross the platform channel as base64-encoded PNG bytes
     * (see [NavigationMarker] on the Dart side) - JSON has no native binary
     * type, and this keeps the Intent extra a plain string like waypoints/
     * options above. Malformed entries (bad base64, undecodable image
     * bytes) are dropped rather than crashing navigation startup.
     */
    private fun parseMarkers(intent: Intent): List<NavigationMarkerData> {
        val json = intent.getStringExtra(EXTRA_MARKERS) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val obj = array.getJSONObject(i)
                val bytes = Base64.decode(obj.getString("icon"), Base64.DEFAULT)
                // Mapbox's annotation image path (ExtensionUtils.toMapboxImage)
                // hard-requires ARGB_8888 and throws otherwise - found only by
                // running this on a real device: decodeByteArray's config
                // depends on the source PNG's color type (e.g. an
                // ImageMagick-generated indexed/paletted PNG decoded as
                // RGB_565), so it must be forced explicitly rather than
                // relying on the decoder's default.
                val decodeOptions = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return@mapNotNull null
                val bitmap =
                    if (decoded.config == Bitmap.Config.ARGB_8888) {
                        decoded
                    } else {
                        decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
                    }
                NavigationMarkerData(
                    id = obj.getString("id"),
                    point = Point.fromLngLat(obj.getDouble("longitude"), obj.getDouble("latitude")),
                    bitmap = bitmap,
                    iconScale = obj.optDouble("iconScale", 1.0)
                )
            }.getOrNull()
        }
    }

    companion object {
        const val EXTRA_RESULT = "com.ax9labs.mapbox_navigation_flutter_v3.RESULT"
        private const val EXTRA_WAYPOINTS = "com.ax9labs.mapbox_navigation_flutter_v3.WAYPOINTS"
        private const val EXTRA_OPTIONS = "com.ax9labs.mapbox_navigation_flutter_v3.OPTIONS"
        private const val EXTRA_MARKERS = "com.ax9labs.mapbox_navigation_flutter_v3.MARKERS"

        const val RESULT_ARRIVED = "arrived"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_ERROR = "error"

        private const val ARRIVAL_DISTANCE_THRESHOLD_METERS = 25.0

        fun newIntent(
            context: Context,
            waypoints: List<Map<String, Any?>>,
            options: Map<String, Any?>,
            markers: List<Map<String, Any?>> = emptyList()
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
            val markersJson =
                JSONArray().apply {
                    markers.forEach { m ->
                        put(
                            JSONObject().apply {
                                put("id", m["id"])
                                put("latitude", m["latitude"])
                                put("longitude", m["longitude"])
                                put("icon", m["icon"])
                                put("iconScale", m["iconScale"])
                            }
                        )
                    }
                }
            val optionsJson = JSONObject(options)
            return Intent(context, NavigationActivity::class.java)
                .putExtra(EXTRA_WAYPOINTS, waypointsJson.toString())
                .putExtra(EXTRA_OPTIONS, optionsJson.toString())
                .putExtra(EXTRA_MARKERS, markersJson.toString())
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
