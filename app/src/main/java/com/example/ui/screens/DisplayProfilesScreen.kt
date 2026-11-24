package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PerformanceProfile
import com.example.ui.PulseTuneUiState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.ShizukuIndigo
import com.example.ui.theme.VoltAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayProfilesScreen(
  state: PulseTuneUiState,
  onApplyHz: (Int) -> Unit,
  onApplyFpsLock: (Int, String, Boolean) -> Unit,
  onTargetPackageChange: (String) -> Unit,
  onLowEndFpsOptimizeChange: (Boolean) -> Unit,
  onRemoveAppFpsRule: (String) -> Unit,
  onApplyAllAppFpsRules: () -> Unit,
  onApplyProfile: (PerformanceProfile) -> Unit,
  onQuickRamBoost: () -> Unit,
  contentPadding: PaddingValues,
) {
  var pkgDraft by remember(state.settings.targetPackage) {
    mutableStateOf(state.settings.targetPackage)
  }

  val presetApps =
    listOf(
      "PUBG / BGMI" to "com.tencent.ig",
      "Mobile Legends" to "com.mobile.legends",
      "Free Fire" to "com.dts.freefireth",
      "Genshin" to "com.miHoYo.GenshinImpact",
      "COD Mobile" to "com.activision.callofduty.shooter",
    )

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding =
        PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = contentPadding.calculateTopPadding() + 8.dp,
          bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Refresh Rate Hz Controller (60 / 90 / 120 / Auto)
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Rounded.DisplaySettings, contentDescription = null, tint = ElectricCyan)
              Spacer(Modifier.width(8.dp))
              Column {
                Text("Refresh Rate Control (Hz)", style = MaterialTheme.typography.titleMedium)
                Text(
                  text =
                    "Hardware modes: ${state.display.supportedHz.joinToString(", ") { "${it}Hz" }} • Auto-applies on click",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              val options = listOf(60 to "60 Hz", 90 to "90 Hz", 120 to "120 Hz", 0 to "Auto")
              options.forEach { (hz, label) ->
                val active = state.settings.targetHz == hz
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color =
                    if (active) ElectricCyan.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  border =
                    BorderStroke(
                      if (active) 1.5.dp else 1.dp,
                      if (active) ElectricCyan else MaterialTheme.colorScheme.outline,
                    ),
                  onClick = { onApplyHz(hz) },
                  modifier = Modifier.weight(1f).testTag("hz_option_$hz"),
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 12.dp),
                  ) {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.titleMedium,
                      color = if (active) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text =
                        when (hz) {
                          60 -> "Saver"
                          90 -> "Fluid"
                          120 -> "Ultra"
                          else -> "Stock"
                        },
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

      // 2. Per-Application FPS Lock Controller (60 / 90 / 120 / Unlock)
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Rounded.Lock, contentDescription = null, tint = VoltAmber)
              Spacer(Modifier.width(8.dp))
              Column {
                Text(
                  "Per-App FPS Lock (60 / 90 / 120)",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text =
                    "GameManager (Android 13–16), FPS override (15–16) & SurfaceFlinger sync (11–12)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            OutlinedTextField(
              value = pkgDraft,
              onValueChange = {
                pkgDraft = it
                onTargetPackageChange(it)
              },
              label = { Text("Application Package Name") },
              placeholder = { Text("e.g. com.tencent.ig") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("target_game_pkg_input"),
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              val installedSet = remember(state.installedApps) {
                state.installedApps.map { it.packageName }.toSet()
              }
              val smartApps = remember(state.installedApps) {
                val installedFirst =
                  state.installedApps.take(8).map { it.appName to it.packageName }
                val matchingPresets = presetApps.filter { installedSet.contains(it.second) }
                (matchingPresets + installedFirst + presetApps)
                  .distinctBy { it.second }
                  .take(8)
              }
              smartApps.forEach { (label, pkg) ->
                AssistChip(
                  onClick = {
                    pkgDraft = pkg
                    onTargetPackageChange(pkg)
                  },
                  label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                  modifier = Modifier.testTag("preset_pkg_$pkg"),
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                  Text(
                    text = "Low-End Frame Stability Boost",
                    style = MaterialTheme.typography.labelLarge,
                  )
                  Text(
                    text =
                      "Adds 0.85x GPU downscale, disables window blurs & latches unsignaled frames",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = state.settings.lowEndFpsOptimize,
                  onCheckedChange = onLowEndFpsOptimizeChange,
                  modifier = Modifier.testTag("low_end_fps_switch"),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              val fpsOptions = listOf(60 to "60 FPS", 90 to "90 FPS", 120 to "120 FPS", 0 to "Unlock")
              fpsOptions.forEach { (fps, label) ->
                val active = state.settings.targetFps == fps
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color =
                    if (active) VoltAmber.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  border =
                    BorderStroke(
                      if (active) 1.5.dp else 1.dp,
                      if (active) VoltAmber else MaterialTheme.colorScheme.outline,
                    ),
                  onClick = {
                    onApplyFpsLock(fps, pkgDraft, state.settings.lowEndFpsOptimize)
                  },
                  modifier = Modifier.weight(1f).testTag("fps_option_$fps"),
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 12.dp),
                  ) {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.labelLarge,
                      color = if (active) VoltAmber else MaterialTheme.colorScheme.onSurface,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = if (fps > 0) "Lock App" else "Default",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            if (state.settings.appFpsRules.isNotEmpty()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Saved Per-App Rules (${state.settings.appFpsRules.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                  )
                  FilledTonalButton(
                    onClick = onApplyAllAppFpsRules,
                    modifier = Modifier.testTag("apply_all_fps_rules_btn"),
                  ) {
                    Icon(
                      Icons.Rounded.PlayArrow,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Apply All")
                  }
                }

                state.settings.appFpsRules.forEach { rule ->
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = rule.packageName,
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                          text =
                            "${rule.targetFps} FPS" +
                              if (rule.lowEndOptimize) " • Low-End Smoothness" else "",
                          style = MaterialTheme.typography.labelSmall,
                          color = VoltAmber,
                        )
                      }
                      IconButton(
                        onClick = {
                          onApplyFpsLock(rule.targetFps, rule.packageName, rule.lowEndOptimize)
                        },
                        modifier = Modifier.testTag("run_rule_${rule.packageName}"),
                      ) {
                        Icon(
                          Icons.Rounded.PlayArrow,
                          contentDescription = "Apply rule for ${rule.packageName}",
                          tint = EmeraldMint,
                        )
                      }
                      IconButton(
                        onClick = { onRemoveAppFpsRule(rule.packageName) },
                        modifier = Modifier.testTag("delete_rule_${rule.packageName}"),
                      ) {
                        Icon(
                          Icons.Rounded.DeleteOutline,
                          contentDescription = "Remove rule for ${rule.packageName}",
                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 3. RAM Boost & Battery Saving Profiles
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
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
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = EmeraldMint)
                Spacer(Modifier.width(8.dp))
                Text("Optimization & Power Profiles", style = MaterialTheme.typography.titleMedium)
              }
              OutlinedButton(
                onClick = onQuickRamBoost,
                modifier = Modifier.testTag("profiles_ram_boost_btn"),
              ) {
                Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Trim RAM")
              }
            }

            ProfileSelectionCard(
              profile = PerformanceProfile.LOW_END_SMOOTH,
              icon = Icons.Rounded.FlashOn,
              accent = ElectricCyan,
              selected = state.settings.activeProfile == PerformanceProfile.LOW_END_SMOOTH,
              badge = "LOW-END DEVICE",
              onApply = { onApplyProfile(PerformanceProfile.LOW_END_SMOOTH) },
            )

            ProfileSelectionCard(
              profile = PerformanceProfile.GAMING_MAX,
              icon = Icons.Rounded.Games,
              accent = VoltAmber,
              selected = state.settings.activeProfile == PerformanceProfile.GAMING_MAX,
              badge = "RAM BOOST + 120HZ",
              onApply = { onApplyProfile(PerformanceProfile.GAMING_MAX) },
            )

            ProfileSelectionCard(
              profile = PerformanceProfile.BATTERY_SAVER,
              icon = Icons.Rounded.BatterySaver,
              accent = EmeraldMint,
              selected = state.settings.activeProfile == PerformanceProfile.BATTERY_SAVER,
              badge = "COOL & BATTERY",
              onApply = { onApplyProfile(PerformanceProfile.BATTERY_SAVER) },
            )

            ProfileSelectionCard(
              profile = PerformanceProfile.BALANCED,
              icon = Icons.Rounded.Tune,
              accent = ShizukuIndigo,
              selected = state.settings.activeProfile == PerformanceProfile.BALANCED,
              badge = "RESET DEFAULTS",
              onApply = { onApplyProfile(PerformanceProfile.BALANCED) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ProfileSelectionCard(
  profile: PerformanceProfile,
  icon: ImageVector,
  accent: Color,
  selected: Boolean,
  badge: String,
  onApply: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color =
      if (selected) accent.copy(alpha = 0.14f)
      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    border =
      BorderStroke(
        if (selected) 1.5.dp else 1.dp,
        if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
      ),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onApply)
        .testTag("profile_card_${profile.id}"),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = profile.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            if (selected) {
              Spacer(Modifier.width(6.dp))
              Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Active profile",
                tint = accent,
                modifier = Modifier.size(16.dp),
              )
            }
          }
          Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
          )
        }
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = profile.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(10.dp))
      Button(
        onClick = onApply,
        modifier = Modifier.fillMaxWidth().testTag("apply_profile_${profile.id}"),
      ) {
        Text(if (selected) "Re-Apply ${profile.title}" else "Activate ${profile.title}")
      }
    }
  }
}
