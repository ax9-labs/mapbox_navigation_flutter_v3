import CoreLocation
import MapboxDirections
import MapboxMaps
import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

/// A stable, switchable error identifier + human-readable message - mirrors
/// Android's error-code design (`NavigationActivity.ERROR_*` constants +
/// `EXTRA_ERROR_MESSAGE`), so `NavigationException.code` means the same
/// thing on both platforms.
struct NavigationPluginError: Error {
    let code: String
    let message: String
}

/// iOS equivalent of Android's `NavigationActivity` - but architecturally
/// different by necessity: iOS's Mapbox Navigation SDK still ships a
/// turnkey drop-in `NavigationViewController` (maneuver banner, trip
/// progress, voice, camera, arrival UI all built in), unlike Android's SDK
/// v3, which dropped its Drop-In UI in favor of standalone components. So
/// where Android's `NavigationActivity` *builds* every piece of UI, this
/// coordinator's job is mostly configuration: resolve an origin, request a
/// route, hand `NavigationViewController` a themed style + congestion
/// config + markers, present it, and translate its dismissal into the same
/// arrived/cancelled/error result contract Android returns.
///
/// One real behavioral difference worth calling out: Android's
/// `arrivalDistanceMeters` is a custom threshold we compute ourselves
/// against `RouteProgress.distanceRemaining`, because we built arrival
/// detection from scratch. iOS's drop-in UI does its own arrival detection
/// internally (surfaced via `didArriveAt waypoint:`) - `arrivalDistanceMeters`
/// has no direct hook into that on iOS and is accepted but currently
/// unused here. Documented in TURN_BY_TURN_UI_REDESIGN_IOS.md rather than
/// silently ignored.
@MainActor
final class NavigationCoordinator: NSObject {
    private var provider: MapboxNavigationProvider?
    private var pointAnnotationManager: PointAnnotationManager?
    private var markers: [MarkerData] = []
    private var startOptions = NavigationStartOptions()
    private var completion: ((Result<String, NavigationPluginError>) -> Void)?
    private let originResolver = OriginResolver()

    private static var isNavigating = false

    func start(
        waypoints: [NavigationWaypointData],
        options: NavigationStartOptions,
        markers: [MarkerData],
        from presentingViewController: UIViewController,
        completion: @escaping (Result<String, NavigationPluginError>) -> Void
    ) {
        guard !waypoints.isEmpty else {
            completion(.failure(NavigationPluginError(code: "NO_WAYPOINTS", message: "At least one waypoint is required")))
            return
        }
        guard !NavigationCoordinator.isNavigating else {
            completion(.failure(NavigationPluginError(code: "ALREADY_NAVIGATING", message: "A navigation session is already in progress")))
            return
        }
        guard hasLocationPermission() else {
            completion(.failure(NavigationPluginError(
                code: "LOCATION_PERMISSION_DENIED",
                message: "Location permission not granted - request when-in-use location authorization before calling startNavigation()"
            )))
            return
        }

        startOptions = options
        self.markers = markers
        self.completion = completion
        NavigationCoordinator.isNavigating = true

        originResolver.resolve { [weak self] origin in
            guard let self else { return }
            guard let origin else {
                self.finish(.failure(NavigationPluginError(
                    code: "LOCATION_UNAVAILABLE",
                    message: "No location available to use as the route origin"
                )))
                return
            }
            self.requestRoute(origin: origin, waypoints: waypoints, presentingViewController: presentingViewController)
        }
    }

    private func hasLocationPermission() -> Bool {
        let status = CLLocationManager().authorizationStatus
        return status == .authorizedWhenInUse || status == .authorizedAlways
    }

    private func requestRoute(
        origin: CLLocationCoordinate2D,
        waypoints: [NavigationWaypointData],
        presentingViewController: UIViewController
    ) {
        let coreConfig = CoreConfig(
            locationSource: startOptions.simulateRoute
                ? .simulation(initialLocation: CLLocation(latitude: origin.latitude, longitude: origin.longitude))
                : .live
        )
        let provider = MapboxNavigationProvider(coreConfig: coreConfig)
        self.provider = provider

        let locale = Locale(identifier: startOptions.language)
        let routeOptions = NavigationRouteOptions(
            waypoints: [Waypoint(coordinate: origin)] + waypoints.map { Waypoint(coordinate: $0.coordinate, name: $0.name) },
            profileIdentifier: ProfileIdentifier(rawValue: "mapbox/\(startOptions.profile)"),
            locale: locale,
            distanceUnit: locale.usesMetricSystem ? .kilometer : .mile
        )

        let request = provider.mapboxNavigation.routingProvider().calculateRoutes(options: routeOptions)
        Task { [weak self] in
            guard let self else { return }
            switch await request.result {
            case .failure(let error):
                self.finish(.failure(NavigationPluginError(code: "ROUTE_REQUEST_FAILED", message: error.localizedDescription)))
            case .success(let navigationRoutes):
                await MainActor.run {
                    self.presentNavigation(navigationRoutes: navigationRoutes, presentingViewController: presentingViewController)
                }
            }
        }
    }

    @MainActor
    private func presentNavigation(
        navigationRoutes: NavigationRoutes,
        presentingViewController: UIViewController
    ) {
        guard let provider else { return }

        let navigationOptions = NavigationOptions(
            mapboxNavigation: provider.mapboxNavigation,
            voiceController: provider.routeVoiceController,
            eventsManager: provider.eventsManager(),
            styles: NavigationThemeFactory.makeStyles(theme: startOptions.theme)
        )
        let navigationViewController = NavigationViewController(
            navigationRoutes: navigationRoutes,
            navigationOptions: navigationOptions
        )
        navigationViewController.modalPresentationStyle = .fullScreen
        navigationViewController.delegate = self

        if startOptions.uiOptions.showTraffic {
            navigationViewController.navigationMapView?.congestionConfiguration =
                NavigationThemeFactory.congestionConfiguration(theme: startOptions.theme)
        }

        if !startOptions.voiceInstructionsEnabled {
            provider.routeVoiceController.speechSynthesizer.muted = true
        }

        renderMarkers(on: navigationViewController)

        presentingViewController.present(navigationViewController, animated: true)
    }

    @MainActor
    private func renderMarkers(on navigationViewController: NavigationViewController) {
        guard !markers.isEmpty, let mapView = navigationViewController.navigationMapView?.mapView else { return }
        let manager = mapView.annotations.makePointAnnotationManager()
        pointAnnotationManager = manager
        manager.annotations = markers.map { marker in
            var annotation = PointAnnotation(coordinate: marker.coordinate)
            annotation.image = .init(image: marker.image, name: marker.id)
            annotation.iconSize = marker.iconScale
            return annotation
        }
    }

    private func finish(_ result: Result<String, NavigationPluginError>) {
        let callback = completion
        completion = nil
        provider = nil
        pointAnnotationManager = nil
        NavigationCoordinator.isNavigating = false
        callback?(result)
    }
}

// MARK: - NavigationViewControllerDelegate

extension NavigationCoordinator: NavigationViewControllerDelegate {
    @MainActor
    func navigationViewControllerDidDismiss(
        _ navigationViewController: NavigationViewController,
        byCanceling canceled: Bool
    ) {
        navigationViewController.dismiss(animated: true)
        finish(.success(canceled ? "cancelled" : "arrived"))
    }
}

/// One-shot fresh-location resolver, falling back to the location manager's
/// cached last location after a timeout - the iOS counterpart to Android's
/// `NavigationActivity.resolveOrigin()`. Same reasoning: a location cache
/// with no freshness bound risks silently using a stale fix as the route
/// origin (that's exactly what bit the Android implementation during
/// testing), so a fresh fix is always attempted first.
private final class OriginResolver: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var onResult: (@MainActor (CLLocationCoordinate2D?) -> Void)?
    private var timeoutWorkItem: DispatchWorkItem?
    private static let timeoutSeconds: TimeInterval = 5

    func resolve(_ onResult: @escaping @MainActor (CLLocationCoordinate2D?) -> Void) {
        self.onResult = onResult
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest

        let timeout = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.manager.stopUpdatingLocation()
            self.complete(self.manager.location?.coordinate)
        }
        timeoutWorkItem = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.timeoutSeconds, execute: timeout)

        manager.startUpdatingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        manager.stopUpdatingLocation()
        complete(location.coordinate)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        manager.stopUpdatingLocation()
        complete(manager.location?.coordinate)
    }

    private func complete(_ coordinate: CLLocationCoordinate2D?) {
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil
        let callback = onResult
        onResult = nil
        Task { @MainActor in
            callback?(coordinate)
        }
    }
}
