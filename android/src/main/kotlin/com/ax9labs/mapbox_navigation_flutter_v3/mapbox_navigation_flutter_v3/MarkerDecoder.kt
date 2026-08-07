package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.mapbox.geojson.Point

internal data class NavigationMarkerData(
    val id: String,
    val point: Point,
    val bitmap: Bitmap,
    val iconScale: Double
)

/**
 * Decodes marker payloads coming from the Dart `startNavigation(markers:
 * ...)` call (PNG-encoded bytes, delivered as a native `ByteArray` -
 * Flutter's `StandardMessageCodec` supports `Uint8List` as a first-class
 * binary type, so no base64 round-trip is needed on either side) into
 * ready-to-render [NavigationMarkerData].
 *
 * Deliberately generous rather than strict: a single malformed or
 * oversized marker is dropped (logged, not thrown) instead of failing the
 * whole navigation session, since markers are a secondary enhancement on
 * top of the primary turn-by-turn feature. The per-icon and total-marker
 * caps are a safety net against pathological input (e.g. a caller
 * accidentally passing a multi-megabyte photo as an "icon") rather than a
 * contract callers are expected to bump into in practice.
 */
internal object MarkerDecoder {
    private const val TAG = "MarkerDecoder"

    /** Generous for a UI marker glyph; well under anything that would meaningfully affect decode time or memory. */
    const val MAX_ICON_BYTES = 300_000

    /**
     * Same-process transport ([PendingNavigationMarkers]) removes the old
     * Binder transaction-size ceiling this used to risk, but decoding N
     * bitmaps still happens synchronously on the main thread during
     * `startNavigation`, so an upper bound keeps navigation start
     * responsive.
     */
    const val MAX_MARKERS = 300

    /**
     * @param decodeIcon Decodes raw icon bytes to a bitmap, or
     *   `null`/throws if it can't. Defaults to the real
     *   `android.graphics.BitmapFactory` path ([decodeArgb8888Bitmap]);
     *   overridable so the field-validation/cap/error-handling logic in
     *   [decodeMarker] can be unit-tested on the plain JVM without
     *   Robolectric - `BitmapFactory` is unavailable (stubbed to throw) in
     *   a standard `testDebugUnitTest` run.
     */
    fun decodeMarkers(
        rawMarkers: List<Map<String, Any?>>,
        decodeIcon: (ByteArray) -> Bitmap? = ::decodeArgb8888Bitmap
    ): List<NavigationMarkerData> {
        if (rawMarkers.size > MAX_MARKERS) {
            Log.w(TAG, "Dropping ${rawMarkers.size - MAX_MARKERS} marker(s) past the $MAX_MARKERS cap")
        }
        return rawMarkers.take(MAX_MARKERS).mapNotNull { decodeMarker(it, decodeIcon) }
    }

    private fun decodeMarker(
        raw: Map<String, Any?>,
        decodeIcon: (ByteArray) -> Bitmap?
    ): NavigationMarkerData? {
        val id = raw["id"] as? String ?: return dropped("marker missing id")
        val latitude = (raw["latitude"] as? Number)?.toDouble() ?: return dropped("marker $id missing latitude")
        val longitude = (raw["longitude"] as? Number)?.toDouble() ?: return dropped("marker $id missing longitude")
        val iconBytes = raw["icon"] as? ByteArray ?: return dropped("marker $id missing icon")
        val iconScale = (raw["iconScale"] as? Number)?.toDouble() ?: 1.0

        if (iconBytes.size > MAX_ICON_BYTES) {
            return dropped("marker $id icon (${iconBytes.size} bytes) exceeds the $MAX_ICON_BYTES byte cap")
        }

        val bitmap =
            runCatching { decodeIcon(iconBytes) }
                .getOrElse { return dropped("marker $id icon could not be decoded: ${it.message}") }
                ?: return dropped("marker $id icon bytes are not a decodable image")

        return NavigationMarkerData(
            id = id,
            point = Point.fromLngLat(longitude, latitude),
            bitmap = bitmap,
            iconScale = iconScale
        )
    }

    /**
     * Mapbox's annotation image path (`ExtensionUtils.toMapboxImage`)
     * hard-requires an `ARGB_8888` bitmap and throws otherwise - found only
     * by running this on a real device: `decodeByteArray`'s resulting
     * config depends on the source PNG's color type (e.g. an
     * ImageMagick-generated indexed/paletted PNG decoded as `RGB_565`), so
     * it must be forced explicitly rather than relying on the decoder's
     * default.
     */
    private fun decodeArgb8888Bitmap(bytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return if (decoded.config == Bitmap.Config.ARGB_8888) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
        }
    }

    private fun dropped(reason: String): NavigationMarkerData? {
        Log.w(TAG, "Dropping marker: $reason")
        return null
    }
}

/**
 * Same-process handoff for decoded marker bitmaps between
 * [MapboxNavigationFlutterV3Plugin.startNavigation] and [NavigationActivity].
 *
 * Markers used to round-trip through the launch `Intent` as a base64 JSON
 * string, which risked `TransactionTooLargeException` (Android's Binder
 * transaction buffer is ~1MB total, shared across the whole Intent) once a
 * handful of real icons were involved - exactly the scenario custom
 * markers exist for. Since the Activity always launches in the same
 * process as the plugin, there's no need to serialize through the
 * Intent/Binder at all; a static holder (same pattern as
 * [MapboxAccessToken]) hands the already-decoded bitmaps across directly.
 * [NavigationActivity] consumes (reads-and-clears) this exactly once in
 * `onCreate()` so a stale value can't leak into an unrelated future
 * session.
 */
internal object PendingNavigationMarkers {
    var value: List<NavigationMarkerData>? = null

    fun consume(): List<NavigationMarkerData> {
        val markers = value ?: emptyList()
        value = null
        return markers
    }
}
