import CoreLocation
import Flutter
import UIKit

/// A decoded, ready-to-render marker - mirrors Android's `NavigationMarkerData`.
struct MarkerData {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let image: UIImage
    let iconScale: Double
}

/// Decodes marker payloads coming from the Dart `startNavigation(markers:
/// ...)` call (PNG-encoded bytes, delivered as `FlutterStandardTypedData` -
/// Flutter's method channel represents a Dart `Uint8List` as that type on
/// iOS, no base64 needed on either side) into ready-to-render
/// [MarkerData].
///
/// Mirrors Android's `MarkerDecoder.kt`: deliberately generous rather than
/// strict - a single malformed or oversized marker is dropped (logged, not
/// thrown) instead of failing the whole navigation session, since markers
/// are a secondary enhancement on top of the primary turn-by-turn feature.
enum MarkerDecoder {
    /// Generous for a UI marker glyph; well under anything that would meaningfully affect decode time or memory.
    static let maxIconBytes = 300_000

    /// Same-process transport means there's no Binder-style size ceiling
    /// to guard against on iOS the way Android's `PendingNavigationMarkers`
    /// had to, but decoding N images still happens synchronously on the
    /// main thread during `startNavigation`, so an upper bound keeps
    /// navigation start responsive.
    static let maxMarkers = 300

    static func decodeMarkers(_ raw: [[String: Any]]?) -> [MarkerData] {
        guard let raw else { return [] }
        if raw.count > maxMarkers {
            NSLog("[MarkerDecoder] Dropping \(raw.count - maxMarkers) marker(s) past the \(maxMarkers) cap")
        }
        return raw.prefix(maxMarkers).compactMap(decodeMarker)
    }

    private static func decodeMarker(_ raw: [String: Any]) -> MarkerData? {
        guard let id = raw["id"] as? String else {
            return dropped("marker missing id")
        }
        guard let latitude = raw["latitude"] as? Double else {
            return dropped("marker \(id) missing latitude")
        }
        guard let longitude = raw["longitude"] as? Double else {
            return dropped("marker \(id) missing longitude")
        }
        guard let iconData = (raw["icon"] as? FlutterStandardTypedData)?.data else {
            return dropped("marker \(id) missing icon")
        }
        let iconScale = raw["iconScale"] as? Double ?? 1.0

        if iconData.count > maxIconBytes {
            return dropped("marker \(id) icon (\(iconData.count) bytes) exceeds the \(maxIconBytes) byte cap")
        }
        guard let image = UIImage(data: iconData) else {
            return dropped("marker \(id) icon bytes are not a decodable image")
        }

        return MarkerData(
            id: id,
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            image: image,
            iconScale: iconScale
        )
    }

    private static func dropped(_ reason: String) -> MarkerData? {
        NSLog("[MarkerDecoder] Dropping marker: \(reason)")
        return nil
    }
}
