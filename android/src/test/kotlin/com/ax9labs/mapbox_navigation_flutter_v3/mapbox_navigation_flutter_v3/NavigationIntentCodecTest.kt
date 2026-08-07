package com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class NavigationIntentCodecTest {
    // --- waypoints ---

    @Test
    fun decodeWaypoints_null_returnsEmptyList() {
        assertTrue(NavigationIntentCodec.decodeWaypoints(null).isEmpty())
    }

    @Test
    fun decodeWaypoints_malformedJson_returnsEmptyListInsteadOfThrowing() {
        assertTrue(NavigationIntentCodec.decodeWaypoints("not json").isEmpty())
    }

    @Test
    fun decodeWaypoints_missingRequiredField_returnsEmptyListInsteadOfThrowing() {
        // "longitude" is missing - JSONObject.getDouble("longitude") throws,
        // which should be caught and degrade to empty rather than crash
        // NavigationActivity.onCreate().
        assertTrue(NavigationIntentCodec.decodeWaypoints("""[{"latitude": 1.0}]""").isEmpty())
    }

    @Test
    fun encodeWaypoints_thenDecodeWaypoints_roundTrips() {
        val raw =
            listOf(
                mapOf("latitude" to 26.2034, "longitude" to -98.2300, "name" to "Safe Zone"),
                mapOf("latitude" to 1.0, "longitude" to 2.0, "name" to null)
            )

        val decoded = NavigationIntentCodec.decodeWaypoints(NavigationIntentCodec.encodeWaypoints(raw))

        assertEquals(2, decoded.size)
        assertEquals(-98.2300, decoded[0].longitude())
        assertEquals(26.2034, decoded[0].latitude())
        assertEquals(2.0, decoded[1].longitude())
        assertEquals(1.0, decoded[1].latitude())
    }

    // --- options ---

    @Test
    fun decodeOptions_null_returnsDefaults() {
        assertEquals(NavigationStartOptions.DEFAULT, NavigationIntentCodec.decodeOptions(null))
    }

    @Test
    fun decodeOptions_malformedJson_returnsDefaultsInsteadOfThrowing() {
        assertEquals(NavigationStartOptions.DEFAULT, NavigationIntentCodec.decodeOptions("not json"))
    }

    @Test
    fun decodeOptions_partialJson_fillsMissingFieldsWithDefaults() {
        val decoded = NavigationIntentCodec.decodeOptions("""{"profile": "walking"}""")

        assertEquals("walking", decoded.profile)
        assertEquals(NavigationStartOptions.DEFAULT.language, decoded.language)
        assertEquals(NavigationStartOptions.DEFAULT.arrivalDistanceMeters, decoded.arrivalDistanceMeters)
        assertEquals(NavigationStartOptions.DEFAULT.simulateSpeedMultiplier, decoded.simulateSpeedMultiplier)
    }

    @Test
    fun encodeOptions_thenDecodeOptions_roundTrips() {
        val raw =
            mapOf(
                "profile" to "cycling",
                "language" to "es",
                "voiceInstructionsEnabled" to false,
                "bannerInstructionsEnabled" to false,
                "simulateRoute" to true,
                "arrivalDistanceMeters" to 10.0,
                "simulateSpeedMultiplier" to 1.5
            )

        val decoded = NavigationIntentCodec.decodeOptions(NavigationIntentCodec.encodeOptions(raw))

        assertEquals(
            NavigationStartOptions(
                profile = "cycling",
                language = "es",
                voiceInstructionsEnabled = false,
                bannerInstructionsEnabled = false,
                simulateRoute = true,
                arrivalDistanceMeters = 10.0,
                simulateSpeedMultiplier = 1.5
            ),
            decoded
        )
    }
}
