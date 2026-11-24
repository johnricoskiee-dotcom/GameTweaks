package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ChipsetVendor
import com.example.data.model.TweakItem
import com.example.data.model.TweakTier
import com.example.ui.PulseTuneUiState
import com.example.ui.components.TierBadge
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.RootCrimson
import com.example.ui.theme.ShizukuIndigo
import com.example.ui.theme.VoltAmber
import kotlin.math.roundToInt

private enum class TweakFilter(val label: String) {
  ALL("All Tweaks"),
  SHIZUKU("Shizuku"),
  ROOT("Root Only"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TweaksConsoleScreen(
  state: PulseTuneUiState,
  onRunTweak: (TweakItem, Boolean) -> Unit,
  onApplyRootTuning: (String, Int) -> Unit,
  onApplyChipsetBoost: (ChipsetVendor, Boolean) -> Unit,
  onRunManualCommand: (String) -> Unit,
  onClearLogs: () -> Unit,
  contentPadding: PaddingValues,
) {
  var selectedFilter by remember { mutableStateOf(TweakFilter.ALL) }
  var selectedChipset by remember(state.cpu.chipsetVendor) {
    mutableStateOf(state.cpu.chipsetVendor)
  }
  var commandInput by remember { mutableStateOf("") }
  var pendingHighRiskTweak by remember { mutableStateOf<TweakItem?>(null) }

  var rootGov by remember(state.settings.rootGovernor) {
    mutableStateOf(state.settings.rootGovernor)
  }
  var rootSwappiness by remember(state.settings.rootSwappiness) {
    mutableFloatStateOf(state.settings.rootSwappiness.toFloat())
  }

  val filteredTweaks =
    remember(state.tweaks, selectedFilter) {
      when (selectedFilter) {
        TweakFilter.ALL -> state.tweaks
        TweakFilter.SHIZUKU -> state.tweaks.filter { it.tier == TweakTier.SHIZUKU }
        TweakFilter.ROOT -> state.tweaks.filter { it.tier == TweakTier.ROOT }
      }
    }

  pendingHighRiskTweak?.let { riskTweak ->
    AlertDialog(
      onDismissRequest = { pendingHighRiskTweak = null },
      icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = RootCrimson) },
      title = { Text("Root Kernel Tweak Confirmation") },
      text = {
        Text(
          text =
            "${riskTweak.title}: ${riskTweak.safetyNote ?: "Modifies root kernel sysfs nodes."} Continue?"
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRunTweak(riskTweak, true)
            pendingHighRiskTweak = null
          },
          modifier = Modifier.testTag("confirm_root_tweak_btn"),
        ) {
          Text("Apply Root Tweak")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingHighRiskTweak = null }) { Text("Cancel") }
      },
    )
  }

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
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Filter Chips Header
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = ElectricCyan)
            Spacer(Modifier.width(8.dp))
            Text("Shizuku & Root System Tweaks", style = MaterialTheme.typography.titleLarge)
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TweakFilter.entries.forEach { filter ->
              FilterChip(
                selected = selectedFilter == filter,
                onClick = { selectedFilter = filter },
                label = { Text(filter.label) },
                modifier = Modifier.testTag("tweak_filter_${filter.name.lowercase()}"),
              )
            }
          }
        }
      }

      // 1b. Chipset Boost Studio (MediaTek & Other Chipsets for Shizuku & Root)
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, VoltAmber.copy(alpha = 0.45f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text("Chipset Boost Studio", style = MaterialTheme.typography.titleMedium)
              Text(
                text =
                  "Detected: ${state.cpu.chipsetVendor.label} (${state.cpu.socModel}) • Zero-bootloop volatile tuning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ChipsetVendor.entries.forEach { vendor ->
                FilterChip(
                  selected = selectedChipset == vendor,
                  onClick = { selectedChipset = vendor },
                  label = { Text(vendor.label) },
                  modifier = Modifier.testTag("chipset_${vendor.name.lowercase()}"),
                )
              }
            }

            Text(
              text = selectedChipset.familyHint,
              style = MaterialTheme.typography.labelMedium,
              color = VoltAmber,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (selectedFilter != TweakFilter.ROOT) {
                Button(
                  onClick = { onApplyChipsetBoost(selectedChipset, false) },
                  modifier = Modifier.weight(1f).testTag("apply_chipset_shizuku_btn"),
                ) {
                  Text("Shizuku Boost")
                }
              }
              if (selectedFilter != TweakFilter.SHIZUKU) {
                OutlinedButton(
                  onClick = { onApplyChipsetBoost(selectedChipset, true) },
                  modifier = Modifier.weight(1f).testTag("apply_chipset_root_btn"),
                ) {
                  Text("Root Boost")
                }
              }
            }
          }
        }
      }

      // 2. Root-Only Interactive Kernel Governor & VM Sliders (shown in All or Root filter)
      if (selectedFilter == TweakFilter.ALL || selectedFilter == TweakFilter.ROOT) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, RootCrimson.copy(alpha = 0.45f)),
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
                  Icon(Icons.Rounded.Security, contentDescription = null, tint = RootCrimson)
                  Spacer(Modifier.width(8.dp))
                  Text("Root Kernel Tuning", style = MaterialTheme.typography.titleMedium)
                }
                TierBadge(tier = TweakTier.ROOT)
              }

              Text(
                text = "CPU Scaling Governor",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("performance", "schedutil", "ondemand", "powersave").forEach { gov ->
                  FilterChip(
                    selected = rootGov == gov,
                    onClick = { rootGov = gov },
                    label = { Text(gov) },
                    modifier = Modifier.testTag("root_gov_$gov"),
                  )
                }
              }

              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "VM Swappiness (ZRAM Aggressiveness)",
                    style = MaterialTheme.typography.labelLarge,
                  )
                  Text(
                    text = "${rootSwappiness.roundToInt()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = RootCrimson,
                  )
                }
                Slider(
                  value = rootSwappiness,
                  onValueChange = { rootSwappiness = it },
                  valueRange = 10f..100f,
                  steps = 8,
                  modifier = Modifier.testTag("root_swappiness_slider"),
                )
              }

              Button(
                onClick = { onApplyRootTuning(rootGov, rootSwappiness.roundToInt()) },
                modifier = Modifier.fillMaxWidth().testTag("apply_root_kernel_btn"),
              ) {
                Text("Apply Root Governor & Swappiness")
              }
            }
          }
        }
      }

      // 3. Tweak Cards
      items(filteredTweaks, key = { it.id }) { tweak ->
        val isEnabled = state.settings.enabledTweakIds.contains(tweak.id)
        TweakItemCard(
          tweak = tweak,
          isEnabled = isEnabled,
          onApply = {
            if (tweak.isHighRisk) {
              pendingHighRiskTweak = tweak
            } else {
              onRunTweak(tweak, true)
            }
          },
          onRevert = { onRunTweak(tweak, false) },
        )
      }

      // 4. On-Device Root / Shizuku Shell Console & Execution Log
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, ShizukuIndigo.copy(alpha = 0.45f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = ElectricCyan)
                Spacer(Modifier.width(8.dp))
                Text("On-Device Shell Console", style = MaterialTheme.typography.titleMedium)
              }
              if (state.logs.isNotEmpty()) {
                IconButton(
                  onClick = onClearLogs,
                  modifier = Modifier.testTag("clear_console_logs_btn"),
                ) {
                  Icon(Icons.Rounded.ClearAll, contentDescription = "Clear execution log")
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = { Text("On-Device Command") },
                placeholder = { Text("settings get system peak_refresh_rate") },
                singleLine = true,
                keyboardOptions =
                  androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                  ),
                keyboardActions =
                  androidx.compose.foundation.text.KeyboardActions(
                    onSend = {
                      onRunManualCommand(commandInput)
                      commandInput = ""
                    }
                  ),
                modifier = Modifier.weight(1f).testTag("console_cmd_input"),
              )
              Button(
                onClick = {
                  onRunManualCommand(commandInput)
                  commandInput = ""
                },
                modifier = Modifier.testTag("console_run_btn"),
              ) {
                Icon(Icons.Rounded.Send, contentDescription = "Run command on device")
              }
            }

            if (state.logs.isEmpty()) {
              Text(
                text =
                  "Execution results appear here whenever you tap any tweak, profile, or command.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.logs.take(10).forEach { log ->
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background,
                    border =
                      BorderStroke(
                        1.dp,
                        if (log.exitCode == 0) EmeraldMint.copy(alpha = 0.35f)
                        else VoltAmber.copy(alpha = 0.45f),
                      ),
                  ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = "${log.timestampLabel} • ${log.mode.shortLabel}",
                          style = MaterialTheme.typography.labelSmall,
                          color = ElectricCyan,
                        )
                        Text(
                          text = "exit ${log.exitCode}",
                          style = MaterialTheme.typography.labelSmall,
                          color = if (log.exitCode == 0) EmeraldMint else VoltAmber,
                        )
                      }
                      Spacer(Modifier.height(3.dp))
                      Text(
                        text = "$ ${log.command}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      if (log.output.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                          text = log.output,
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
        }
      }
    }
  }
}

@Composable
private fun TweakItemCard(
  tweak: TweakItem,
  isEnabled: Boolean,
  onApply: () -> Unit,
  onRevert: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    border =
      BorderStroke(
        1.dp,
        if (isEnabled) EmeraldMint.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
      ),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.testTag("tweak_card_${tweak.id}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TierBadge(tier = tweak.tier)
        Text(
          text = tweak.apiSupportLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = tweak.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = tweak.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (tweak.safetyNote != null) {
        Text(
          text = "Note: ${tweak.safetyNote}",
          style = MaterialTheme.typography.labelSmall,
          color = VoltAmber,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onApply,
          modifier = Modifier.weight(1f).testTag("apply_tweak_${tweak.id}"),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (isEnabled) "Re-Apply" else "Apply")
        }
        if (tweak.revertCommands.isNotEmpty()) {
          OutlinedButton(
            onClick = onRevert,
            modifier = Modifier.weight(1f).testTag("revert_tweak_${tweak.id}"),
          ) {
            Icon(
              Icons.Rounded.RestartAlt,
              contentDescription = null,
              modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Revert")
          }
        }
      }
    }
  }
}
