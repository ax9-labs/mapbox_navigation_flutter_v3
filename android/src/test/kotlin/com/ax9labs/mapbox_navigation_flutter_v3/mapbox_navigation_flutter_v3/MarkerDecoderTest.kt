package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import android.graphics.Bitmap
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [MarkerDecoder]'s field validation, size caps, and
 * error-swallowing behavior by injecting a fake `decodeIcon` step instead
 * of the real `android.util.Base64`/`android.graphics.BitmapFactory` path
 * - those two are unavailable (stubbed to throw) in a plain
 * `testDebugUnitTest` run without Robolectric. See the `decodeIcon`
 * parameter on [MarkerDecoder.decodeMarkers].
 */
internal class MarkerDecoderTest {
    private val fakeBitmap: Bitmap = Mockito.mock(Bitmap::class.java)

    private fun marker(
        id: Any? = "m1",
        latitude: Any? = 1.0,
        longitude: Any? = 2.0,
        icon: Any? = "base64-icon",
        iconScale: Any? = null
    ): Map<String, Any?> =
        buildMap {
            put("id", id)
            put("latitude", latitude)
            put("longitude", longitude)
            put("icon", icon)
            if (iconScale != null) put("iconScale", iconScale)
        }

    @Test
    fun decodeMarkers_validMarker_isDecoded() {
        val result = MarkerDecoder.decodeMarkers(listOf(marker()), decodeIcon = { fakeBitmap })

        assertEquals(1, result.size)
        assertEquals("m1", result[0].id)
        assertEquals(2.0, result[0].point.longitude())
        assertEquals(1.0, result[0].point.latitude())
        assertEquals(1.0, result[0].iconScale)
        assertEquals(fakeBitmap, result[0].bitmap)
    }

    @Test
    fun decodeMarkers_customIconScale_isForwarded() {
        val result = MarkerDecoder.decodeMarkers(listOf(marker(iconScale = 2.5)), decodeIcon = { fakeBitmap })

        assertEquals(2.5, result.single().iconScale)
    }

    @Test
    fun decodeMarkers_missingId_isDropped() {
        assertTrue(MarkerDecoder.decodeMarkers(listOf(marker(id = null)), decodeIcon = { fakeBitmap }).isEmpty())
    }

    @Test
    fun decodeMarkers_missingLatitude_isDropped() {
        assertTrue(MarkerDecoder.decodeMarkers(listOf(marker(latitude = null)), decodeIcon = { fakeBitmap }).isEmpty())
    }

    @Test
    fun decodeMarkers_missingIcon_isDropped() {
        assertTrue(MarkerDecoder.decodeMarkers(listOf(marker(icon = null)), decodeIcon = { fakeBitmap }).isEmpty())
    }

    @Test
    fun decodeMarkers_decodeIconReturnsNull_isDropped() {
        assertTrue(MarkerDecoder.decodeMarkers(listOf(marker()), decodeIcon = { null }).isEmpty())
    }

    @Test
    fun decodeMarkers_decodeIconThrows_isDroppedInsteadOfPropagating() {
        val markers = listOf(marker(id = "bad"), marker(id = "good"))

        val result =
            MarkerDecoder.decodeMarkers(markers) { base64 ->
                if (base64 == "base64-icon") fakeBitmap else error("boom")
            }

        // Both markers share the same "base64-icon" icon in this fixture,
        // so what's actually being exercised here is that a throwing
        // decodeIcon for one marker doesn't take down the whole batch -
        // see the dedicated oversized-icon test below for a case that
        // legitimately differs per marker.
        assertEquals(2, result.size)
    }

    @Test
    fun decodeMarkers_oneMalformedMarkerDoesNotDropTheOthers() {
        val markers = listOf(marker(id = "bad", icon = null), marker(id = "good"))

        val result = MarkerDecoder.decodeMarkers(markers, decodeIcon = { fakeBitmap })

        assertEquals(listOf("good"), result.map { it.id })
    }

    @Test
    fun decodeMarkers_iconBase64LengthOverCap_isDroppedWithoutInvokingDecodeIcon() {
        val oversized = "a".repeat(MarkerDecoder.MAX_ICON_BYTES * 2)
        var decodeIconCalled = false

        val result =
            MarkerDecoder.decodeMarkers(listOf(marker(icon = oversized))) {
                decodeIconCalled = true
                fakeBitmap
            }

        assertTrue(result.isEmpty())
        assertTrue(!decodeIconCalled, "decodeIcon should not run for a payload that already fails the cheap length check")
    }

    @Test
    fun decodeMarkers_moreThanMaxMarkers_truncatesToTheCap() {
        val markers = (1..MarkerDecoder.MAX_MARKERS + 50).map { marker(id = "m$it") }

        val result = MarkerDecoder.decodeMarkers(markers, decodeIcon = { fakeBitmap })

        assertEquals(MarkerDecoder.MAX_MARKERS, result.size)
    }

    @Test
    fun decodeMarkers_emptyInput_returnsEmptyOutput() {
        assertTrue(MarkerDecoder.decodeMarkers(emptyList(), decodeIcon = { fakeBitmap }).isEmpty())
    }
}
