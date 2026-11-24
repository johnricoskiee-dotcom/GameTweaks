package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FpsOverlayConfig
import com.example.data.model.PerformanceProfile
import com.example.data.model.ThermalCategory
import com.example.ui.PulseTuneUiState
import com.example.ui.components.ArcGauge
import com.example.ui.components.FloatingOverlayCard
import com.example.ui.components.ThermalSparklineChart
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.ShizukuIndigo
import com.example.ui.theme.ThermalCoral
import com.example.ui.theme.VoltAmber
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
  state: PulseTuneUiState,
  hasOverlayPermission: Boolean,
  onQuickRamBoost: () -> Unit,
  onSmoothTouchBoost: () -> Unit,
  onSuperSmoothBoost: () -> Unit,
  onToggleOverlay: () -> Unit,
  onOverlayConfigChange: (FpsOverlayConfig) -> Unit,
  onResetOverlayPosition: () -> Unit,
  onApplyProfile: (PerformanceProfile) -> Unit,
  onApplyHz: (Int) -> Unit,
  onOpenPrivilegeDialog: () -> Unit,
  contentPadding: PaddingValues,
) {
  val listState = rememberLazyListState()
  val thermalColor =
    when {
      state.thermal.maxTempC >= 46f || state.thermal.androidThermalStatus >= 3 -> ThermalCoral
      state.thermal.maxTempC >= 39f || state.thermal.androidThermalStatus >= 1 -> VoltAmber
      else -> EmeraldMint
    }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding =
        PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = contentPadding.calculateTopPadding() + 8.dp,
          bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Hero Telemetry Header Card with generated silicon artwork
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Box(modifier = Modifier.fillMaxWidth()) {
            Image(
              painter = painterResource(id = R.drawable.img_telemetry_banner),
              contentDescription = "Hardware telemetry backdrop",
              contentScale = ContentScale.Crop,
              alpha = 0.35f,
              modifier = Modifier.matchParentSize(),
            )
            Box(
              modifier =
                Modifier.matchParentSize()
                  .background(
                    Brush.horizontalGradient(
                      listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                      )
                    )
                  )
            )

            Column(modifier = Modifier.padding(18.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  val compat = state.compatibility
                  Text(
                    text =
                      if (compat != null) "${compat.manufacturer} ${compat.model}" else "Device",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text =
                      if (compat != null)
                        "${compat.androidVersionName} (API ${compat.apiLevel}) • ${state.cpu.abi}"
                      else "System Telemetry",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                  )
                }
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = ElectricCyan.copy(alpha = 0.14f),
                  border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                ) {
                  Text(
                    text = "${state.display.currentHz} Hz Active",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }

              Spacer(Modifier.height(12.dp))

              // Auto-detected Hardware Spec Matrix: CPU, GPU, RAM, Storage
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  HardwareSpecChip(
                    label = "CPU",
                    value =
                      "${state.cpu.socModel} (${state.cpu.coreCount}C • ${state.cpu.cpuLoadPercent}%)",
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f),
                  )
                  HardwareSpecChip(
                    label = "GPU",
                    value = state.cpu.gpuModel.ifBlank { "Detecting..." },
                    accent = EmeraldMint,
                    modifier = Modifier.weight(1f),
                  )
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  val totalRamGb = state.memory.totalRamMb / 1024f
                  val usedRamGb = state.memory.usedRamMb / 1024f
                  HardwareSpecChip(
                    label = "RAM (${state.memory.usedPercent}%)",
                    value =
                      String.format(
                        Locale.US,
                        "%.1f / %.1f GB",
                        usedRamGb,
                        totalRamGb,
                      ),
                    accent = VoltAmber,
                    modifier = Modifier.weight(1f),
                  )
                  HardwareSpecChip(
                    label = "STORAGE (${state.storage.usedPercent}%)",
                    value =
                      String.format(
                        Locale.US,
                        "%.1f / %.1f GB",
                        state.storage.usedGb,
                        state.storage.totalGb,
                      ),
                    accent = ShizukuIndigo,
                    modifier = Modifier.weight(1f),
                  )
                }
              }

              Spacer(Modifier.height(12.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                HeroMetricTile(
                  label = "Live Frame",
                  value = "${state.display.measuredFps} FPS",
                  sub = String.format(Locale.US, "%.1f ms", state.display.frameTimeMs),
                  accent = EmeraldMint,
                  modifier = Modifier.weight(1f),
                )
                HeroMetricTile(
                  label = "Peak Heat",
                  value = String.format(Locale.US, "%.1f°C", state.thermal.maxTempC),
                  sub = state.thermal.androidThermalLabel,
                  accent = thermalColor,
                  modifier = Modifier.weight(1f),
                )
                HeroMetricTile(
                  label = "Free Storage",
                  value = String.format(Locale.US, "%.1f GB", state.storage.availableGb),
                  sub = "${state.memory.availableRamMb} MB RAM free",
                  accent = VoltAmber,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      // 1b. Persistent Floating FPS Counter Overlay Studio
      item {
        FloatingOverlayCard(
          config = state.settings.overlayConfig,
          isRunning = state.isOverlayRunning,
          hasOverlayPermission = hasOverlayPermission,
          measuredFps = state.display.measuredFps,
          currentHz = state.display.currentHz,
          batteryTempC = state.thermal.batteryTempC,
          ramUsedPercent = state.memory.usedPercent,
          onToggleOverlay = onToggleOverlay,
          onConfigChange = onOverlayConfigChange,
          onResetPosition = onResetOverlayPosition,
        )
      }

      // 2. Quick Actions Row: RAM Boost + Quick Hz
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = VoltAmber)
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "Quick Performance Controls",
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              Text(
                text = state.settings.activeProfile.title,
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = onQuickRamBoost,
                modifier = Modifier.weight(1f).testTag("quick_ram_boost_btn"),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
              ) {
                Icon(
                  Icons.Rounded.CleaningServices,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("RAM Boost", maxLines = 1)
              }

              FilledTonalButton(
                onClick = onSmoothTouchBoost,
                modifier = Modifier.weight(1f).testTag("quick_smooth_touch_btn"),
              ) {
                Icon(
                  Icons.Rounded.TouchApp,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Smooth Touch", maxLines = 1)
              }
            }

            val maxSupportedHz = state.display.supportedHz.maxOrNull()?.coerceAtLeast(60) ?: 120
            Button(
              onClick = onSuperSmoothBoost,
              modifier = Modifier.fillMaxWidth().testTag("quick_super_smooth_btn"),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = EmeraldMint,
                  contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
              Icon(
                Icons.Rounded.Bolt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Super Smooth Phone (${maxSupportedHz}Hz + Zero Lag)",
                fontWeight = FontWeight.Bold,
              )
            }

            FilledTonalButton(
              onClick = { onApplyProfile(PerformanceProfile.GAMING_MAX) },
              modifier = Modifier.fillMaxWidth().testTag("quick_gaming_profile_btn"),
            ) {
              Icon(
                Icons.Rounded.Speed,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text("Activate Gaming Max + 120Hz")
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf(60, 90, 120).forEach { hz ->
                val isSelected = state.settings.targetHz == hz || state.display.currentHz == hz
                val supported = state.display.supportedHz.contains(hz)
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color =
                    if (isSelected) ElectricCyan.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                  border =
                    BorderStroke(
                      1.dp,
                      if (isSelected) ElectricCyan else MaterialTheme.colorScheme.outline,
                    ),
                  onClick = { onApplyHz(hz) },
                  modifier = Modifier.weight(1f).testTag("quick_hz_${hz}_btn"),
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 10.dp),
                  ) {
                    Text(
                      text = "${hz}Hz",
                      style = MaterialTheme.typography.titleMedium,
                      color =
                        if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = if (supported) "Hardware" else "Auto Set",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 3. Detailed Heat & Thermal Monitoring Card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, thermalColor.copy(alpha = 0.4f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Thermostat, contentDescription = null, tint = thermalColor)
                Spacer(Modifier.width(8.dp))
                Column {
                  Text(
                    text = "Detailed Heat Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text =
                      "Android Thermal Status: ${state.thermal.androidThermalLabel}" +
                        (state.thermal.thermalHeadroomPercent?.let {
                          " • Headroom ${it.toInt()}%"
                        } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              ArcGauge(
                valueFraction = ((state.thermal.maxTempC - 20f) / 60f).coerceIn(0f, 1f),
                centerText = String.format(Locale.US, "%.0f°", state.thermal.maxTempC),
                subLabel = "PEAK",
                accentColor = thermalColor,
                modifier = Modifier.size(72.dp),
              )
            }

            // Battery power & thermal row
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Rounded.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (state.thermal.isCharging) EmeraldMint else VoltAmber,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(Modifier.width(8.dp))
                  Column {
                    Text(
                      text =
                        "Battery ${state.thermal.batteryLevel}% (${state.thermal.batteryHealthLabel})",
                      style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                      text =
                        String.format(
                          Locale.US,
                          "%.1f°C • %d mV • %d mA",
                          state.thermal.batteryTempC,
                          state.thermal.batteryVoltageMv,
                          state.thermal.batteryCurrentMa,
                        ),
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Text(
                  text = if (state.thermal.isCharging) "CHARGING" else "DISCHARGING",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (state.thermal.isCharging) EmeraldMint else VoltAmber,
                )
              }
            }

            // Thermal Sparkline Chart
            ThermalSparklineChart(
              history = state.history,
              modifier = Modifier.fillMaxWidth(),
            )

            // Individual SoC / Thermal Zones
            if (state.thermal.zones.isNotEmpty()) {
              Text(
                text = "Active Thermal Zones (${state.thermal.zones.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                state.thermal.zones.forEach { zone ->
                  val zColor =
                    when {
                      zone.tempCelsius >= 55f -> ThermalCoral
                      zone.tempCelsius >= 42f -> VoltAmber
                      else -> ElectricCyan
                    }
                  val catTag =
                    when (zone.category) {
                      ThermalCategory.CPU -> "CPU"
                      ThermalCategory.GPU -> "GPU"
                      ThermalCategory.BATTERY -> "BAT"
                      ThermalCategory.SKIN -> "SKIN"
                      ThermalCategory.PMIC -> "PMIC"
                      ThermalCategory.OTHER -> "SOC"
                    }
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = zColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, zColor.copy(alpha = 0.3f)),
                  ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                      Text(
                        text = "$catTag • ${zone.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Text(
                        text = String.format(Locale.US, "%.1f °C", zone.tempCelsius),
                        style = MaterialTheme.typography.labelLarge,
                        color = zColor,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }
              }
            }

            if (state.thermal.sysfsAccessRestricted) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = ShizukuIndigo.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ShizukuIndigo.copy(alpha = 0.35f)),
                onClick = onOpenPrivilegeDialog,
                modifier = Modifier.fillMaxWidth().testTag("unlock_thermal_zones_banner"),
              ) {
                Text(
                  text =
                    "SELinux restricts /sys/class/thermal on unprivileged UID. Tap to enable Shizuku or Root for per-core SoC & GPU die probes.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }
          }
        }
      }

      // 4. Memory (RAM + ZRAM) & CPU Core Frequencies Card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, contentDescription = null, tint = ElectricCyan)
                Spacer(Modifier.width(8.dp))
                Text("Memory & ZRAM Telemetry", style = MaterialTheme.typography.titleMedium)
              }
              if (state.memory.isLowRamDevice) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = VoltAmber.copy(alpha = 0.18f),
                ) {
                  Text(
                    text = "LOW-RAM DEVICE",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoltAmber,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }
            }

            LinearProgressIndicator(
              progress = { (state.memory.usedPercent / 100f).coerceIn(0f, 1f) },
              color = if (state.memory.usedPercent > 82) ThermalCoral else ElectricCyan,
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
              strokeCap = StrokeCap.Round,
              modifier = Modifier.fillMaxWidth().height(9.dp),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Used: ${state.memory.usedRamMb} / ${state.memory.totalRamMb} MB",
                style = MaterialTheme.typography.labelMedium,
              )
              Text(
                text =
                  if (state.memory.zramTotalMb > 0)
                    "ZRAM Free: ${state.memory.zramFreeMb}/${state.memory.zramTotalMb} MB"
                  else "LowMem Limit: ${state.memory.lowMemoryThresholdMb} MB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Spacer(Modifier.height(4.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Rounded.DeveloperBoard,
                  contentDescription = null,
                  tint = EmeraldMint,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "CPU (${state.cpu.coreCount} Cores • ${state.cpu.currentGovernor})",
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              Text(
                text = "${state.cpu.cpuLoadPercent}% Load",
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldMint,
              )
            }

            if (state.cpu.coreFrequenciesMhz.isNotEmpty()) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.cpu.coreFrequenciesMhz.forEachIndexed { idx, curMhz ->
                  val maxMhz = state.cpu.maxFrequenciesMhz.getOrElse(idx) { 0 }
                  val frac =
                    if (maxMhz > 0 && curMhz > 0) (curMhz.toFloat() / maxMhz).coerceIn(0.05f, 1f)
                    else 0.1f
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(
                      text = "CPU$idx",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.width(38.dp),
                    )
                    Box(
                      modifier =
                        Modifier.weight(1f)
                          .height(7.dp)
                          .clip(RoundedCornerShape(4.dp))
                          .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                      Box(
                        modifier =
                          Modifier.fillMaxWidth(frac)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                              if (frac > 0.85f) VoltAmber else EmeraldMint
                            )
                      )
                    }
                    Text(
                      text = if (curMhz > 0) "${curMhz} MHz" else "Restricted",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.width(74.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 5. Display Capabilities Footer
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                Icons.Rounded.DisplaySettings,
                contentDescription = null,
                tint = ElectricCyan,
              )
              Spacer(Modifier.width(10.dp))
              Column {
                Text(
                  text =
                    "Hardware Modes: ${state.display.supportedHz.joinToString(" / ") { "${it}Hz" }}",
                  style = MaterialTheme.typography.labelLarge,
                )
                Text(
                  text = "ABI: ${state.cpu.abi} • Jank Frames: ${state.display.jankFrames}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Icon(
              Icons.Rounded.Refresh,
              contentDescription = "Live polling active",
              tint = EmeraldMint,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      // 6. Owner Credit Footer
      item {
        val context = androidx.compose.ui.platform.LocalContext.current
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = ElectricCyan.copy(alpha = 0.09f),
          border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f)),
          onClick = {
            runCatching {
              val intent =
                android.content.Intent(
                  android.content.Intent.ACTION_VIEW,
                  android.net.Uri.parse("https://t.me/Blaze00"),
                )
              context.startActivity(intent)
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("owner_telegram_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "GameTweaks • Owner: Blaze00",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "Telegram: @Blaze00 • Tap to open Telegram",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
              )
            }
            Text(
              text = "TG @Blaze00",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = ElectricCyan,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HardwareSpecChip(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun HeroMetricTile(
  label: String,
  value: String,
  sub: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(3.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accent,
      )
      Text(
        text = sub,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
