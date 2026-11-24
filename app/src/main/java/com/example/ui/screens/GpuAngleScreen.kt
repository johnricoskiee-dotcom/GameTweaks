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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AngleBackend
import com.example.data.model.AngleConfig
import com.example.data.model.GpuConfig
import com.example.data.model.GpuRenderer
import com.example.ui.PulseTuneUiState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.VoltAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GpuAngleScreen(
  state: PulseTuneUiState,
  onSaveAngle: (AngleConfig) -> Unit,
  onSaveGpu: (GpuConfig) -> Unit,
  onApplyPreset: (String) -> Unit,
  contentPadding: PaddingValues,
) {
  var angleDraft by remember(state.settings.angleConfig) {
    mutableStateOf(state.settings.angleConfig)
  }
  var gpuDraft by remember(state.settings.gpuConfig) { mutableStateOf(state.settings.gpuConfig) }
  var newPkgInput by remember { mutableStateOf("") }

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
      // 1. One-Tap GPU Presets
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = VoltAmber)
              Spacer(Modifier.width(8.dp))
              Text("GPU & ANGLE Quick Presets", style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text =
                "Pre-configured driver stacks for lag-free gaming or ultra-smooth low-end frame pacing.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = { onApplyPreset("Competitive Gaming") },
                modifier = Modifier.weight(1f).testTag("gpu_preset_gaming"),
              ) {
                Text("Gaming Max")
              }
              OutlinedButton(
                onClick = { onApplyPreset("Low-End Smooth") },
                modifier = Modifier.weight(1f).testTag("gpu_preset_lowend"),
              ) {
                Text("Low-End")
              }
              OutlinedButton(
                onClick = { onApplyPreset("Stock Reset") },
                modifier = Modifier.weight(1f).testTag("gpu_preset_stock"),
              ) {
                Text("Reset")
              }
            }
          }
        }
      }

      // 2. ANGLE Graphics Driver Settings (Android 11-16)
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Rounded.Layers, contentDescription = null, tint = ElectricCyan)
              Spacer(Modifier.width(8.dp))
              Column {
                Text("ANGLE Graphics Driver", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "OpenGL ES over Vulkan translation • Auto-applies on selection",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            ToggleRow(
              title = "Enable ANGLE Globally",
              subtitle = "Routes all OpenGL ES apps through ANGLE (persist.graphics.egl on 15–16)",
              checked = angleDraft.enabledGlobally,
              onCheckedChange = {
                val updated = angleDraft.copy(enabledGlobally = it)
                angleDraft = updated
                onSaveAngle(updated)
              },
              tag = "angle_global_switch",
            )

            ToggleRow(
              title = "Show ANGLE In-Use Toast",
              subtitle = "Displays confirmation banner when a game launches on ANGLE",
              checked = angleDraft.showInUseDialog,
              onCheckedChange = {
                val updated = angleDraft.copy(showInUseDialog = it)
                angleDraft = updated
                onSaveAngle(updated)
              },
              tag = "angle_toast_switch",
            )

            Text(
              text = "ANGLE Translation Backend",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              AngleBackend.entries.forEach { backend ->
                FilterChip(
                  selected = angleDraft.backend == backend,
                  onClick = {
                    val updated = angleDraft.copy(backend = backend)
                    angleDraft = updated
                    onSaveAngle(updated)
                  },
                  label = { Text(backend.label) },
                  modifier = Modifier.testTag("angle_backend_${backend.propValue}"),
                )
              }
            }

            // Per-App ANGLE Override Packages
            Text(
              text = "Per-App ANGLE Packages",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = newPkgInput,
                onValueChange = { newPkgInput = it },
                label = { Text("Add App Package") },
                placeholder = { Text("com.tencent.ig") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("angle_pkg_input"),
              )
              Button(
                onClick = {
                  val clean = newPkgInput.trim()
                  if (clean.isNotEmpty() && !angleDraft.anglePackages.contains(clean)) {
                    angleDraft =
                      angleDraft.copy(anglePackages = angleDraft.anglePackages + clean)
                    newPkgInput = ""
                  }
                },
                modifier = Modifier.testTag("angle_add_pkg_btn"),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add package")
              }
            }

            if (angleDraft.anglePackages.isNotEmpty()) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                angleDraft.anglePackages.forEach { pkg ->
                  InputChip(
                    selected = true,
                    onClick = {
                      angleDraft =
                        angleDraft.copy(anglePackages = angleDraft.anglePackages - pkg)
                    },
                    label = { Text(pkg, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                      Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove $pkg",
                        modifier = Modifier.size(16.dp),
                      )
                    },
                  )
                }
              }
            }

            Button(
              onClick = { onSaveAngle(angleDraft) },
              modifier = Modifier.fillMaxWidth().testTag("apply_angle_btn"),
            ) {
              Text("Apply ANGLE Configuration")
            }
          }
        }
      }

      // 3. GPU Profile & HWUI Pipeline Configurations
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.4f)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = EmeraldMint)
              Spacer(Modifier.width(8.dp))
              Column {
                Text("GPU Profile & HWUI Pipeline", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "Tap any renderer or switch to auto-apply to your phone",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Text(
              text = "Rendering Backend (Vulkan / OpenGL ES / SkiaGL / SkiaVK)",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              GpuRenderer.entries.chunked(2).forEach { pair ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  pair.forEach { renderer ->
                    val selected = gpuDraft.renderer == renderer
                    Surface(
                      shape = RoundedCornerShape(14.dp),
                      color =
                        if (selected) EmeraldMint.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                      border =
                        BorderStroke(
                          if (selected) 1.5.dp else 1.dp,
                          if (selected) EmeraldMint else MaterialTheme.colorScheme.outline,
                        ),
                      onClick = {
                        val updated = gpuDraft.copy(renderer = renderer)
                        gpuDraft = updated
                        onSaveGpu(updated)
                      },
                      modifier =
                        Modifier.weight(1f).testTag("gpu_renderer_${renderer.propValue}"),
                    ) {
                      Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                          text = renderer.label,
                          style = MaterialTheme.typography.titleMedium,
                          color =
                            if (selected) EmeraldMint else MaterialTheme.colorScheme.onSurface,
                          fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                          text = renderer.subtitle,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                }
              }
            }

            ToggleRow(
              title = "Force GPU Hardware Composition",
              subtitle = "Sets debug.sf.hw=1 & debug.egl.hw=1 for consistent frame rendering",
              checked = gpuDraft.forceGpuComposition,
              onCheckedChange = {
                val updated = gpuDraft.copy(forceGpuComposition = it)
                gpuDraft = updated
                onSaveGpu(updated)
              },
              tag = "gpu_comp_switch",
            )

            ToggleRow(
              title = "Disable SurfaceFlinger Backpressure",
              subtitle = "Reduces input-to-display latency in fast shooters & MOBA games",
              checked = gpuDraft.disableBackpressure,
              onCheckedChange = {
                val updated = gpuDraft.copy(disableBackpressure = it)
                gpuDraft = updated
                onSaveGpu(updated)
              },
              tag = "gpu_backpressure_switch",
            )

            ToggleRow(
              title = "Latch Unsignaled Buffers",
              subtitle = "Allows SurfaceFlinger to latch early buffers for smoother frame pacing",
              checked = gpuDraft.latchUnsignaled,
              onCheckedChange = {
                val updated = gpuDraft.copy(latchUnsignaled = it)
                gpuDraft = updated
                onSaveGpu(updated)
              },
              tag = "gpu_latch_switch",
            )

            ToggleRow(
              title = "Disable HW Overlays (SurfaceFlinger 1008)",
              subtitle = "Routes all display layers through GPU instead of Display Hardware Composer",
              checked = gpuDraft.disableHwOverlays,
              onCheckedChange = {
                val updated = gpuDraft.copy(disableHwOverlays = it)
                gpuDraft = updated
                onSaveGpu(updated)
              },
              tag = "gpu_overlays_switch",
            )

            ToggleRow(
              title = "Disable System Blur Shaders",
              subtitle = "Massive FPS uplift on low-end Adreno/Mali GPUs",
              checked = gpuDraft.disableBlurOverhead,
              onCheckedChange = {
                val updated = gpuDraft.copy(disableBlurOverhead = it)
                gpuDraft = updated
                onSaveGpu(updated)
              },
              tag = "gpu_blurs_switch",
            )

            ToggleRow(
              title = "Force 4x MSAA (Visual Sharpness)",
              subtitle = "Increases edge quality in OpenGL ES 2.0/3.0 games (higher GPU load)",
              checked = gpuDraft.forceMsaa4x,
              onCheckedChange = {
                val updated = gpuDraft.copy(forceMsaa4x = it)
                gpuDraft = updated
                onSaveGpu(updated)
              },
              tag = "gpu_msaa_switch",
            )

            Button(
              onClick = { onSaveGpu(gpuDraft) },
              modifier = Modifier.fillMaxWidth().testTag("apply_gpu_profile_btn"),
            ) {
              Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Apply GPU Profile Configuration")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  tag: String,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.testTag(tag),
      )
    }
  }
}
