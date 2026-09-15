package com.example.ui.dashboard

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppBottomNavTab {
  BERANDA,
  PETA,
  TITIK_PANAS,
  INFO
}

@Composable
fun MyNavigationBottomBar(
  currentTab: AppBottomNavTab,
  onTabSelected: (AppBottomNavTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val activeGreen = Color(0xFF00E676)
  val unselectedSlate = Color(0xFF78909C)

  NavigationBar(
    modifier = modifier.testTag("bottom_navigation_bar"),
    containerColor = Color(0xFF0D1520),
    tonalElevation = 8.dp
  ) {
    // 1. Beranda
    NavigationBarItem(
      selected = currentTab == AppBottomNavTab.BERANDA,
      onClick = { onTabSelected(AppBottomNavTab.BERANDA) },
      icon = {
        Icon(
          imageVector = Icons.Default.Home,
          contentDescription = "Beranda",
          modifier = Modifier.size(24.dp)
        )
      },
      label = {
        Text(
          text = "Beranda",
          fontSize = 11.sp,
          fontWeight = if (currentTab == AppBottomNavTab.BERANDA) FontWeight.Bold else FontWeight.Normal
        )
      },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = activeGreen,
        selectedTextColor = activeGreen,
        indicatorColor = activeGreen.copy(alpha = 0.15f),
        unselectedIconColor = unselectedSlate,
        unselectedTextColor = unselectedSlate
      ),
      modifier = Modifier.testTag("nav_tab_beranda")
    )

    // 2. Peta
    NavigationBarItem(
      selected = currentTab == AppBottomNavTab.PETA,
      onClick = { onTabSelected(AppBottomNavTab.PETA) },
      icon = {
        Icon(
          imageVector = Icons.Default.Map,
          contentDescription = "Peta",
          modifier = Modifier.size(24.dp)
        )
      },
      label = {
        Text(
          text = "Peta",
          fontSize = 11.sp,
          fontWeight = if (currentTab == AppBottomNavTab.PETA) FontWeight.Bold else FontWeight.Normal
        )
      },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = activeGreen,
        selectedTextColor = activeGreen,
        indicatorColor = activeGreen.copy(alpha = 0.15f),
        unselectedIconColor = unselectedSlate,
        unselectedTextColor = unselectedSlate
      ),
      modifier = Modifier.testTag("nav_tab_peta")
    )

    // 3. Titik Panas
    NavigationBarItem(
      selected = currentTab == AppBottomNavTab.TITIK_PANAS,
      onClick = { onTabSelected(AppBottomNavTab.TITIK_PANAS) },
      icon = {
        Icon(
          imageVector = Icons.Default.LocalFireDepartment,
          contentDescription = "Titik Panas",
          modifier = Modifier.size(24.dp)
        )
      },
      label = {
        Text(
          text = "Titik Panas",
          fontSize = 11.sp,
          fontWeight = if (currentTab == AppBottomNavTab.TITIK_PANAS) FontWeight.Bold else FontWeight.Normal
        )
      },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = activeGreen,
        selectedTextColor = activeGreen,
        indicatorColor = activeGreen.copy(alpha = 0.15f),
        unselectedIconColor = unselectedSlate,
        unselectedTextColor = unselectedSlate
      ),
      modifier = Modifier.testTag("nav_tab_titik_panas")
    )

    // 4. Info
    NavigationBarItem(
      selected = currentTab == AppBottomNavTab.INFO,
      onClick = { onTabSelected(AppBottomNavTab.INFO) },
      icon = {
        Icon(
          imageVector = Icons.Default.Info,
          contentDescription = "Info",
          modifier = Modifier.size(24.dp)
        )
      },
      label = {
        Text(
          text = "Info",
          fontSize = 11.sp,
          fontWeight = if (currentTab == AppBottomNavTab.INFO) FontWeight.Bold else FontWeight.Normal
        )
      },
      colors = NavigationBarItemDefaults.colors(
        selectedIconColor = activeGreen,
        selectedTextColor = activeGreen,
        indicatorColor = activeGreen.copy(alpha = 0.15f),
        unselectedIconColor = unselectedSlate,
        unselectedTextColor = unselectedSlate
      ),
      modifier = Modifier.testTag("nav_tab_info")
    )
  }
}
