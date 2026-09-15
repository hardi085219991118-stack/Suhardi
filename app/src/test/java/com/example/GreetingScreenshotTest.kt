package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.ui.FoundationScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h1800dp-xhdpi", sdk = [36])
class DashboardScreenComposeTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun dashboardScreen_displaysPermissionRequiredState() {
    composeTestRule.setContent {
      MyApplicationTheme {
        DashboardScreen(
          state = DashboardState(
            locationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED
          )
        )
      }
    }

    // 1. Header & App Title
    composeTestRule.onNodeWithTag("app_title").assertIsDisplayed()

    // 2. Fire Detection card: Data Belum Tersedia & Count '--'
    composeTestRule.onNodeWithTag("fire_detection_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("fire_count_value").assertIsDisplayed()
    composeTestRule.onNodeWithText("--").assertIsDisplayed()
    composeTestRule.onNodeWithText("FIRE DATA SOURCE NOT VERIFIED").assertIsDisplayed()

    // 3. Primary action buttons section
    composeTestRule.onNodeWithTag("primary_action_buttons_section").assertIsDisplayed()
  }

  @Test
  fun realLocationCard_displaysPermissionRequiredState() {
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.dashboard.RealLocationCard(
          state = DashboardState(locationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED),
          onRequestPermission = {},
          onRefreshLocation = {},
          onOpenSettings = {},
          onOpenLocationSettings = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("location_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_status_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("request_permission_button").assertIsDisplayed()
  }

  @Test
  fun realLocationCard_displaysPermissionDeniedState() {
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.dashboard.RealLocationCard(
          state = DashboardState(
            locationStatus = LocationStatus.LOCATION_PERMISSION_DENIED,
            locationErrorMessage = "LOCATION PERMISSION DENIED: Akses lokasi ditolak"
          ),
          onRequestPermission = {},
          onRefreshLocation = {},
          onOpenSettings = {},
          onOpenLocationSettings = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("location_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_denied_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("retry_permission_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("open_settings_button").assertIsDisplayed()
  }

  @Test
  fun realLocationCard_displaysProviderDisabledState() {
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.dashboard.RealLocationCard(
          state = DashboardState(
            locationStatus = LocationStatus.LOCATION_PROVIDER_DISABLED
          ),
          onRequestPermission = {},
          onRefreshLocation = {},
          onOpenSettings = {},
          onOpenLocationSettings = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("location_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_provider_disabled_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("enable_gps_button").assertIsDisplayed()
  }

  @Test
  fun realLocationCard_displaysLocationAvailableState() {
    val fixTime = 1700000000000L
    val testLocation = DeviceLocation(
      latitude = -2.123456,
      longitude = 114.654321,
      accuracyMeters = 7.5f,
      timeMillis = fixTime,
      provider = "gps"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.dashboard.RealLocationCard(
          state = DashboardState(
            locationStatus = LocationStatus.LOCATION_AVAILABLE,
            deviceLocation = testLocation
          ),
          onRequestPermission = {},
          onRefreshLocation = {},
          onOpenSettings = {},
          onOpenLocationSettings = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("location_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_latitude_value").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_longitude_value").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_accuracy_value").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_time_value").assertIsDisplayed()
    composeTestRule.onNodeWithTag("refresh_location_button").assertIsDisplayed()
  }

  @Test
  fun realLocationCard_displaysLocationErrorState() {
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.dashboard.RealLocationCard(
          state = DashboardState(
            locationStatus = LocationStatus.LOCATION_ERROR,
            locationErrorMessage = "GPS timeout searching for satellites"
          ),
          onRequestPermission = {},
          onRefreshLocation = {},
          onOpenSettings = {},
          onOpenLocationSettings = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("location_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_error_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("retry_location_button").assertIsDisplayed()
  }

  @Test
  fun dashboardScreen_canNavigateToFoundationContractForAudit() {
    composeTestRule.setContent {
      MyApplicationTheme {
        DashboardScreen()
      }
    }

    // Navigasi ke Layar Kontrak & Registri via Tab Info
    composeTestRule.onNodeWithTag("nav_tab_info").performClick()
    composeTestRule.onNodeWithTag("view_contract_button").performClick()

    // Verifikasi Foundation Contract & Registry tetap tampil (Regression Check)
    composeTestRule.onNodeWithTag("zero_dummy_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("evidence_status_card").assertIsDisplayed()

    // Kembali ke Dashboard & Tab Beranda
    composeTestRule.onNodeWithTag("back_to_dashboard_button").performClick()
    composeTestRule.onNodeWithTag("nav_tab_beranda").performClick()
    composeTestRule.onNodeWithTag("fire_detection_card").assertIsDisplayed()
  }

  @Test
  fun foundationScreen_regressionCheck() {
    composeTestRule.setContent {
      MyApplicationTheme {
        FoundationScreen()
      }
    }

    composeTestRule.onNodeWithTag("app_title").assertIsDisplayed()
    composeTestRule.onNodeWithTag("zero_dummy_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("evidence_status_card").assertIsDisplayed()
  }
}
