package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.fire.FireDataRecord
import com.example.core.share.FireHotspotShareHelper
import java.util.Locale

enum class HotspotSortOrder {
  TERBARU,
  TERDEKAT,
  FRP_TERTINGGI
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotsListScreen(
  state: DashboardState,
  onSelectRecord: (FireDataRecord) -> Unit,
  onOpenFilter: () -> Unit,
  filterCriteria: HotspotFilterCriteria,
  onResetFilter: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var sortOrder by remember { mutableStateOf(HotspotSortOrder.TERBARU) }
  var showSortDropdown by remember { mutableStateOf(false) }

  // Filter records using single source of truth or HotspotFilterHelper
  val filteredRecords = remember(state.filteredFireRecords, state.fireRecords, filterCriteria, state.deviceLocation) {
    if (state.filteredFireRecords.isNotEmpty() || state.fireRecords.isEmpty()) {
      state.filteredFireRecords
    } else {
      com.example.core.fire.HotspotFilterHelper.filterRecords(
        records = state.fireRecords,
        criteria = filterCriteria,
        deviceLocation = state.deviceLocation
      )
    }
  }

  // Sort records
  val sortedRecords = remember(filteredRecords, sortOrder, state.deviceLocation) {
    when (sortOrder) {
      HotspotSortOrder.TERBARU -> filteredRecords.sortedByDescending { it.acquisitionTimestampMillis }
      HotspotSortOrder.TERDEKAT -> {
        if (state.deviceLocation != null) {
          filteredRecords.sortedBy { record: FireDataRecord ->
            FireHotspotShareHelper.calculateDistanceKm(
              state.deviceLocation.latitude,
              state.deviceLocation.longitude,
              record.latitude,
              record.longitude
            )
          }
        } else {
          filteredRecords
        }
      }
      HotspotSortOrder.FRP_TERTINGGI -> filteredRecords.sortedByDescending { it.frp ?: 0.0 }
    }
  }

  val isFiltered = filterCriteria.maxDistanceKm != null || filterCriteria.satellite != null || filterCriteria.maxAgeHours != null
  val totalCount = state.fireRecords.size
  val filteredCount = sortedRecords.size
  val indicatorText = if (isFiltered) {
    "Menampilkan $filteredCount dari $totalCount titik panas"
  } else {
    "Menampilkan $totalCount titik panas"
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("hotspots_list_screen"),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Daftar Titik Panas",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = indicatorText,
              style = MaterialTheme.typography.bodySmall,
              color = if (isFiltered) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          IconButton(onClick = onOpenFilter, modifier = Modifier.testTag("open_filter_button")) {
            Icon(
              imageVector = Icons.Default.FilterList,
              contentDescription = "Penyaring",
              tint = if (isFiltered) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurface
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp)
    ) {
      // Sort and filter summary row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box {
          TextButton(
            onClick = { showSortDropdown = true },
            modifier = Modifier.testTag("sort_dropdown_button")
          ) {
            Text(
              text = when (sortOrder) {
                HotspotSortOrder.TERBARU -> "Urutkan: Terbaru ▼"
                HotspotSortOrder.TERDEKAT -> "Urutkan: Terdekat ▼"
                HotspotSortOrder.FRP_TERTINGGI -> "Urutkan: FRP Tertinggi ▼"
              },
              color = Color(0xFFFF7043),
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
          }
          DropdownMenu(
            expanded = showSortDropdown,
            onDismissRequest = { showSortDropdown = false }
          ) {
            DropdownMenuItem(
              text = { Text("Terbaru") },
              onClick = {
                sortOrder = HotspotSortOrder.TERBARU
                showSortDropdown = false
              }
            )
            DropdownMenuItem(
              text = { Text("Terdekat") },
              onClick = {
                sortOrder = HotspotSortOrder.TERDEKAT
                showSortDropdown = false
              }
            )
            DropdownMenuItem(
              text = { Text("FRP Tertinggi") },
              onClick = {
                sortOrder = HotspotSortOrder.FRP_TERTINGGI
                showSortDropdown = false
              }
            )
          }
        }

        if (isFiltered) {
          TextButton(onClick = onResetFilter) {
            Text("Atur Ulang Penyaring ✕", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
          }
        }
      }

      if (sortedRecords.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.LocalFireDepartment,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.outline,
              modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (state.fireRecords.isEmpty()) "Belum ada titik panas yang terdeteksi." else "Tidak ada titik panas yang sesuai dengan penyaring.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isFiltered) {
              Spacer(modifier = Modifier.height(12.dp))
              OutlinedButton(onClick = onResetFilter) {
                Text("Atur Ulang Penyaring")
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(sortedRecords) { record ->
            val distanceKm = if (state.deviceLocation != null) {
              FireHotspotShareHelper.calculateDistanceKm(
                state.deviceLocation.latitude,
                state.deviceLocation.longitude,
                record.latitude,
                record.longitude
              )
            } else null

            HotspotItemCard(
              record = record,
              distanceKm = distanceKm,
              onClick = { onSelectRecord(record) },
              onShare = { FireHotspotShareHelper.shareHotspot(context, record, distanceKm) }
            )
          }
          item {
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun HotspotItemCard(
  record: FireDataRecord,
  distanceKm: Double?,
  onClick: () -> Unit,
  onShare: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Flame Icon on left
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(Color(0xFFFF5722).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.LocalFireDepartment,
          contentDescription = null,
          tint = Color(0xFFFF5722),
          modifier = Modifier.size(24.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      // Content
      Column(modifier = Modifier.weight(1f)) {
        // Top line: Time & Distance
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${record.acqDate} ${record.acqTime.ifBlank { "--" }} UTC",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )
          if (distanceKm != null) {
            Text(
              text = String.format(Locale.US, "%.1f km >", distanceKm),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = Color(0xFF00C853)
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Coordinates
        Text(
          text = String.format(Locale.US, "%.5f°, %.5f°", record.latitude, record.longitude),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Satellite & Instrument
        Text(
          text = "${record.satellite.ifBlank { "Satelit NRT" }} | ${record.instrument.ifBlank { "VIIRS" }}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(2.dp))

        // FRP & Confidence
        Text(
          text = "FRP ${if (record.frp != null) "${record.frp} MW" else "-"} | Keyakinan ${record.confidence ?: "-"}",
          style = MaterialTheme.typography.bodySmall.copy(
            color = Color(0xFFFFB74D),
            fontWeight = FontWeight.SemiBold
          )
        )
      }

      // Quick share button
      IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
        Icon(
          imageVector = Icons.Default.Share,
          contentDescription = "Bagikan",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
