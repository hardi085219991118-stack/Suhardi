package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class HotspotFilterCriteria(
  val maxDistanceKm: Double? = null,
  val satellite: String? = null,
  val maxAgeHours: Int? = null
)

@Composable
fun FilterHotspotsDialog(
  currentCriteria: HotspotFilterCriteria,
  onApplyCriteria: (HotspotFilterCriteria) -> Unit,
  onDismiss: () -> Unit
) {
  var selectedDistance by remember { mutableStateOf(currentCriteria.maxDistanceKm) }
  var selectedSatellite by remember { mutableStateOf(currentCriteria.satellite) }
  var selectedAgeHours by remember { mutableStateOf(currentCriteria.maxAgeHours) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
        .testTag("filter_hotspots_dialog")
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Penyaring Titik Panas",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
              )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Sesuaikan tampilan titik panas pada peta dan daftar.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Tutup")
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Jarak dari lokasi saya
        Text(
          text = "Jarak dari lokasi saya",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFFFF7043)
        )
        Spacer(modifier = Modifier.height(6.dp))

        val distanceOptions = listOf(
          null to "Semua jarak",
          5.0 to "Sampai 5 km",
          10.0 to "Sampai 10 km",
          25.0 to "Sampai 25 km",
          50.0 to "Sampai 50 km"
        )
        distanceOptions.forEach { (dist, label) ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedDistance = dist }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(
              selected = selectedDistance == dist,
              onClick = { selectedDistance = dist },
              colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF5722))
            )
            Text(
              text = label,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(start = 8.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(12.dp))

        // 2. Satelit
        Text(
          text = "Satelit",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFFFF7043)
        )
        Spacer(modifier = Modifier.height(6.dp))

        val satelliteOptions = listOf(
          null to "Semua satelit",
          "NOAA-21" to "NOAA-21",
          "NOAA-20" to "NOAA-20",
          "Suomi-NPP" to "Suomi-NPP",
          "MODIS" to "MODIS (Terra / Aqua)"
        )
        satelliteOptions.forEach { (sat, label) ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedSatellite = sat }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(
              selected = selectedSatellite == sat,
              onClick = { selectedSatellite = sat },
              colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF5722))
            )
            Text(
              text = label,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(start = 8.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(12.dp))

        // 3. Waktu Data
        Text(
          text = "Waktu data",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFFFF7043)
        )
        Spacer(modifier = Modifier.height(6.dp))

        val ageOptions = listOf(
          null to "Semua data",
          6 to "Kurang dari 6 jam",
          12 to "Kurang dari 12 jam",
          24 to "Kurang dari 24 jam"
        )
        ageOptions.forEach { (age, label) ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedAgeHours = age }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(
              selected = selectedAgeHours == age,
              onClick = { selectedAgeHours = age },
              colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF5722))
            )
            Text(
              text = label,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(start = 8.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Button(
          onClick = {
            onApplyCriteria(
              HotspotFilterCriteria(
                maxDistanceKm = selectedDistance,
                satellite = selectedSatellite,
                maxAgeHours = selectedAgeHours
              )
            )
            onDismiss()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("apply_filter_button"),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Tampilkan Hasil", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
          onClick = {
            selectedDistance = null
            selectedSatellite = null
            selectedAgeHours = null
            onApplyCriteria(HotspotFilterCriteria())
            onDismiss()
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reset_filter_button")
        ) {
          Text("Atur Ulang", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}
