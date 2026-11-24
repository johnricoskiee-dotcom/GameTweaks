package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FpsOverlayConfig
import com.example.data.model.OverlayColorPreset
import com.example.data.model.OverlayFontSize
import com.example.data.model.OverlayStyle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ThermalCoral
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingOverlayCard(
  config: FpsOverlayConfig,
  isRunning: Boolean,
  hasOverlayPermission: Boolean,
  measuredFps: Int,
  currentHz: Int,
  batteryTempC: Float,
  ramUsedPercent: Int,
  onToggleOverlay: () -> Unit,
  onConfigChange: (FpsOverlayConfig) -> Unit,
  onResetPosition: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accentColor = Color(config.colorPreset.argbHex)

  Card(
    shape = RoundedCornerShape(20.dp),
    border =
      BorderStroke(
        1.dp,
        if (isRunning) EmeraldMint.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
      ),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier.fillMaxWidth(),
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null, tint = ElectricCyan)
          Spacer(Modifier.width(8.dp))
          Column {
            Text("Floating FPS Counter Overlay", style = MaterialTheme.typography.titleMedium)
            Text(
              text =
                when {
                  isRunning -> "Active over other apps • Drag HUD to move"
                  hasOverlayPermission -> "Ready to float persistently over games"
                  else -> "Requires Display Over Other Apps permission"
                },
              style = MaterialTheme.typography.bodySmall,
              color = if (isRunning) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color =
            if (isRunning) EmeraldMint.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Text(
            text = if (isRunning) "LIVE" else "OFF",
            style = MaterialTheme.typography.labelSmall,
            color = if (isRunning) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      // Live Preview Box
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "HUD Preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = ObsidianBg.copy(alpha = config.opacity.coerceIn(0.2f, 0.98f)),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
          ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
              Text(
                text =
                  when (config.style) {
                    OverlayStyle.COMPACT_PILL -> "$measuredFps FPS"
                    OverlayStyle.GAMING_HUD ->
                      if (config.showRefreshRate) "$measuredFps FPS • ${currentHz}Hz"
                      else "$measuredFps FPS"
                    OverlayStyle.FULL_TELEMETRY -> "$measuredFps FPS (16.6ms)"
                  },
                fontSize = config.fontSize.spValue.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
              )
              if (config.style != OverlayStyle.COMPACT_PILL) {
                val metaParts = buildList {
                  if (config.style == OverlayStyle.FULL_TELEMETRY && config.showRefreshRate) {
                    add("${currentHz}Hz")
                  }
                  if (config.showTemp && batteryTempC > 0f) {
                    add(String.format(Locale.US, "%.1f°C", batteryTempC))
                  }
                  if (config.showRam || config.style == OverlayStyle.FULL_TELEMETRY) {
                    add("RAM $ramUsedPercent%")
                  }
                }
                if (metaParts.isNotEmpty()) {
                  Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      // Start / Stop Overlay Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onToggleOverlay,
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (isRunning) ThermalCoral else MaterialTheme.colorScheme.primary,
              contentColor =
                if (isRunning) Color.White else MaterialTheme.colorScheme.onPrimary,
            ),
          modifier = Modifier.weight(1f).testTag("toggle_fps_overlay_btn"),
        ) {
          Icon(
            imageVector =
              when {
                isRunning -> Icons.Rounded.Stop
                hasOverlayPermission -> Icons.Rounded.PlayArrow
                else -> Icons.Rounded.Layers
              },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text =
              when {
                isRunning -> "Stop Floating FPS"
                hasOverlayPermission -> "Launch Floating FPS"
                else -> "Grant Overlay & Start"
              }
          )
        }

        OutlinedButton(
          onClick = onResetPosition,
          modifier = Modifier.testTag("reset_overlay_pos_btn"),
        ) {
          Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset overlay position", modifier = Modifier.size(18.dp))
        }
      }

      // Style chips
      Text(
        text = "HUD Layout Style",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OverlayStyle.entries.forEach { style ->
          FilterChip(
            selected = config.style == style,
            onClick = { onConfigChange(config.copy(style = style)) },
            label = { Text(style.label) },
            modifier = Modifier.testTag("overlay_style_${style.name.lowercase()}"),
          )
        }
      }

      // Text Size & Color Swatches
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Font Size",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverlayFontSize.entries.forEach { sizeOption ->
              FilterChip(
                selected = config.fontSize == sizeOption,
                onClick = { onConfigChange(config.copy(fontSize = sizeOption)) },
                label = { Text(sizeOption.label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("overlay_size_${sizeOption.name.lowercase()}"),
              )
            }
          }
        }
      }

      Column {
        Text(
          text = "Accent Color",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OverlayColorPreset.entries.forEach { preset ->
            val selected = config.colorPreset == preset
            val swatch = Color(preset.argbHex)
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(38.dp)
                  .clip(CircleShape)
                  .background(swatch.copy(alpha = 0.22f))
                  .clickable { onConfigChange(config.copy(colorPreset = preset)) }
                  .testTag("overlay_color_${preset.name.lowercase()}"),
            ) {
              Box(
                modifier =
                  Modifier.size(if (selected) 24.dp else 18.dp)
                    .clip(CircleShape)
                    .background(swatch)
              )
            }
          }
        }
      }

      // Opacity Slider
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Background Opacity",
            style = MaterialTheme.typography.labelLarge,
          )
          Text(
            text = "${(config.opacity * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = ElectricCyan,
          )
        }
        Slider(
          value = config.opacity,
          onValueChange = { onConfigChange(config.copy(opacity = it)) },
          valueRange = 0.2f..0.95f,
          modifier = Modifier.testTag("overlay_opacity_slider"),
        )
      }

      // Metric & Touch Lock Toggles
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        FilterChip(
          selected = config.showRefreshRate,
          onClick = { onConfigChange(config.copy(showRefreshRate = !config.showRefreshRate)) },
          label = { Text("Show Hz") },
          modifier = Modifier.testTag("overlay_toggle_hz"),
        )
        FilterChip(
          selected = config.showTemp,
          onClick = { onConfigChange(config.copy(showTemp = !config.showTemp)) },
          label = { Text("Show Temp") },
          modifier = Modifier.testTag("overlay_toggle_temp"),
        )
        FilterChip(
          selected = config.showRam,
          onClick = { onConfigChange(config.copy(showRam = !config.showRam)) },
          label = { Text("Show RAM %") },
          modifier = Modifier.testTag("overlay_toggle_ram"),
        )
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
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              imageVector = if (config.lockPosition) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
              contentDescription = null,
              tint = ElectricCyan,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
              Text("Lock Position (Touch Passthrough)", style = MaterialTheme.typography.labelLarge)
              Text(
                text = "Prevents accidental dragging during gameplay",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Switch(
            checked = config.lockPosition,
            onCheckedChange = { onConfigChange(config.copy(lockPosition = it)) },
            modifier = Modifier.testTag("overlay_lock_position_switch"),
          )
        }
      }
    }
  }
}
