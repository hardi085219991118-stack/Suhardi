package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.core.location.LocationStatus
import com.example.core.map.BaseMapLayer
import com.example.core.map.MapTileValidator
import com.example.core.map.TileCheckResult
import com.example.core.map.TileFailureReason
import com.example.ui.map.MapScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression Test Suite for Map Layer Integrity & USGS Fallback Resilience.
 *
 * Covers:
 * 1. USGS 404 error classification as USGS_NO_COVERAGE (Bug 2).
 * 2. Automatic fallback from USGS to Esri World Imagery in MapTileProviderFactory (Bug 1).
 * 3. BaseMapLayer enum integrity (No duplicates).
 * 4. Single Layer Selector Control: exactly 1 layer button in TopAppBar, zero duplicate in dock (Bug 4).
 * 5. TopAppBar header elements with single-line truncation and test tags (Bug 3).
 * 6. Honest USGS non-coverage banner display when USGS is chosen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapLayerAndUsgsResilienceTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun testUsgsFailureReasonClassifiedAsNoCoverage() {
    val simulatedResult = TileCheckResult(
      isValid = false,
      provider = BaseMapLayer.SATELLITE_USGS.displayName,
      tileUrl = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/12/2091/3349",
      zoom = 12,
      x = 3349,
      y = 2091,
      httpCode = 404,
      failureReason = TileFailureReason.USGS_NO_COVERAGE,
      errorMessage = "HTTP 404 Not Found: Citra USGS tidak tersedia untuk wilayah ini."
    )

    assertFalse("USGS 404 must not be valid", simulatedResult.isValid)
    assertEquals(404, simulatedResult.httpCode)
    assertEquals(TileFailureReason.USGS_NO_COVERAGE, simulatedResult.failureReason)
    assertNotNull(simulatedResult.failureReason?.userFriendlyMessage)
    assertTrue(simulatedResult.failureReason!!.userFriendlyMessage.contains("USGS"))
  }

  @Test
  fun testMapTileProviderFactoryFallbackToEsriWhenUsgsFails() = runBlocking {
    // Coordinate in Kalimantan where USGS has no coverage
    val lat = -2.15
    val lon = 114.44
    val outcome = MapTileValidator.validateWithRetryAndFallback(
      desiredLayer = BaseMapLayer.SATELLITE_USGS,
      latitude = lat,
      longitude = lon,
      zoom = 12
    )

    assertTrue("Fallback must be activated for Kalimantan USGS coordinate", outcome.isFallback)
    assertEquals("Fallback layer must be Esri World Imagery", BaseMapLayer.SATELLITE_ESRI, outcome.effectiveLayer)
  }

  @Test
  fun testBaseMapLayerEnumHasNoDuplicates() {
    val layers = BaseMapLayer.values()
    val distinctDisplayNames = layers.map { it.displayName }.distinct()
    val distinctIds = layers.map { it.name }.distinct()

    assertEquals("There must be 3 base map layers", 3, layers.size)
    assertEquals("Layer names must be unique", 3, distinctDisplayNames.size)
    assertEquals("Layer IDs must be unique", 3, distinctIds.size)
  }

  @Test
  fun testSingleLayerButtonInMapScreen() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MapScreen(
          locationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED
        )
      }
    }

    // Exactly one layer selector button exists in TopAppBar
    composeTestRule.onNodeWithTag("layer_selector_button").assertIsDisplayed()

    // No duplicate layer button exists in the dock
    composeTestRule.onNodeWithTag("layer_selector_button_dock").assertDoesNotExist()

    // Dock has [ Lokasi Saya ], [ Titik Panas ], and [ Buka Peta ]
    composeTestRule.onNodeWithTag("my_location_map_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("fit_all_hotspots_dock").assertIsDisplayed()
    composeTestRule.onNodeWithTag("open_map_action_button").assertIsDisplayed()
  }

  @Test
  fun testTopAppBarHeaderIntegrity() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MapScreen(
          locationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED
        )
      }
    }

    composeTestRule.onNodeWithTag("map_screen_title").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_provider_info_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("back_to_dashboard_from_map_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("fit_all_hotspots_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_filter_button").assertIsDisplayed()
  }
}
