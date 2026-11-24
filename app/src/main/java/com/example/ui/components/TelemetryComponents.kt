package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PrivilegeMode
import com.example.data.model.RootStatus
import com.example.data.model.ShizukuStatus
import com.example.data.model.TelemetryHistoryPoint
import com.example.data.model.TweakTier
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.RootCrimson
import com.example.ui.theme.ShizukuIndigo
import com.example.ui.theme.ThermalCoral
import com.example.ui.theme.VoltAmber

@Composable
fun PrivilegeStatusBadge(
  activeMode: PrivilegeMode,
  shizukuStatus: ShizukuStatus,
  rootStatus: RootStatus,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val rootReady = rootStatus is RootStatus.Available
  val shizukuReady = shizukuStatus is ShizukuStatus.Ready
  val effectiveMode =
    when {
      activeMode == PrivilegeMode.ROOT && rootReady -> PrivilegeMode.ROOT
      activeMode == PrivilegeMode.SHIZUKU && shizukuReady -> PrivilegeMode.SHIZUKU
      activeMode == PrivilegeMode.STANDARD && rootReady -> PrivilegeMode.ROOT
      activeMode == PrivilegeMode.STANDARD && shizukuReady -> PrivilegeMode.SHIZUKU
      else -> activeMode
    }
  val isReady =
    when (effectiveMode) {
      PrivilegeMode.SHIZUKU -> shizukuReady
      PrivilegeMode.ROOT -> rootReady
      PrivilegeMode.STANDARD -> true
    }
  val accentColor by
    animateColorAsState(
      targetValue =
        when (effectiveMode) {
          PrivilegeMode.ROOT -> if (isReady) RootCrimson else VoltAmber
          PrivilegeMode.SHIZUKU -> if (isReady) ShizukuIndigo else VoltAmber
          PrivilegeMode.STANDARD -> ElectricCyan
        },
      label = "privilegeAccent",
    )

  Surface(
    shape = RoundedCornerShape(50),
    color = accentColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(50))
        .clickable(onClick = onClick)
        .testTag("privilege_status_badge"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Box(
        modifier =
          Modifier.size(8.dp)
            .clip(CircleShape)
            .background(if (isReady) EmeraldMint else VoltAmber)
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text =
          when (effectiveMode) {
            PrivilegeMode.SHIZUKU ->
              if (shizukuReady) "SHIZUKU ACTIVE" else "SHIZUKU SETUP"
            PrivilegeMode.ROOT ->
              if (rootReady) "ROOT UID 0" else "NO ROOT"
            PrivilegeMode.STANDARD -> "AUTO / DEVICE"
          },
        style = MaterialTheme.typography.labelMedium,
        color = accentColor,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
fun TierBadge(tier: TweakTier, modifier: Modifier = Modifier) {
  val (bg, fg) =
    when (tier) {
      TweakTier.ROOT -> RootCrimson.copy(alpha = 0.16f) to RootCrimson
      TweakTier.SHIZUKU -> ShizukuIndigo.copy(alpha = 0.16f) to ShizukuIndigo
      TweakTier.STANDARD -> ElectricCyan.copy(alpha = 0.15f) to ElectricCyan
    }
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = bg,
    border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
    modifier = modifier,
  ) {
    Text(
      text = tier.badge,
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun ArcGauge(
  valueFraction: Float,
  centerText: String,
  subLabel: String,
  accentColor: Color,
  trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  modifier: Modifier = Modifier,
) {
  val clamped = valueFraction.coerceIn(0f, 1f)
  Box(contentAlignment = Alignment.Center, modifier = modifier) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokePx = 10.dp.toPx()
      val pad = strokePx / 2f
      val arcSize = Size(size.width - strokePx, size.height - strokePx)
      drawArc(
        color = trackColor,
        startAngle = 140f,
        sweepAngle = 260f,
        useCenter = false,
        topLeft = Offset(pad, pad),
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )
      drawArc(
        brush = Brush.sweepGradient(listOf(accentColor.copy(alpha = 0.65f), accentColor)),
        startAngle = 140f,
        sweepAngle = 260f * clamped,
        useCenter = false,
        topLeft = Offset(pad, pad),
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = centerText,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun ThermalSparklineChart(
  history: List<TelemetryHistoryPoint>,
  modifier: Modifier = Modifier,
) {
  val lineColor = ThermalCoral
  val cpuColor = ElectricCyan
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Live Thermal & CPU Timeline",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          LegendDot(color = ThermalCoral, label = "Peak °C")
          LegendDot(color = ElectricCyan, label = "CPU %")
        }
      }
      Spacer(Modifier.height(10.dp))
      Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
        val w = size.width
        val h = size.height
        // Subtle horizontal grid lines
        for (i in 1..3) {
          val y = h * (i / 4f)
          drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.dp.toPx(),
          )
        }

        if (history.size < 2) return@Canvas

        val stepX = w / (history.size - 1).coerceAtLeast(1)

        // CPU Load path (0..100%)
        val cpuPath = Path()
        history.forEachIndexed { idx, pt ->
          val x = idx * stepX
          val y = h - (pt.cpuLoadPercent.coerceIn(0, 100) / 100f) * (h - 8.dp.toPx()) - 4.dp.toPx()
          if (idx == 0) cpuPath.moveTo(x, y) else cpuPath.lineTo(x, y)
        }
        drawPath(
          path = cpuPath,
          color = cpuColor.copy(alpha = 0.78f),
          style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Thermal Peak path (20°C..85°C mapped smoothly)
        val tempPath = Path()
        val tempFill = Path()
        history.forEachIndexed { idx, pt ->
          val x = idx * stepX
          val norm = ((pt.maxTempC - 20f) / 65f).coerceIn(0f, 1f)
          val y = h - norm * (h - 8.dp.toPx()) - 4.dp.toPx()
          if (idx == 0) {
            tempPath.moveTo(x, y)
            tempFill.moveTo(x, h)
            tempFill.lineTo(x, y)
          } else {
            tempPath.lineTo(x, y)
            tempFill.lineTo(x, y)
          }
          if (idx == history.lastIndex) {
            tempFill.lineTo(x, h)
            tempFill.close()
          }
        }
        drawPath(
          path = tempFill,
          brush =
            Brush.verticalGradient(
              listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.0f))
            ),
        )
        drawPath(
          path = tempPath,
          color = lineColor,
          style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(5.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun PrivilegeEngineDialog(
  activeMode: PrivilegeMode,
  shizukuStatus: ShizukuStatus,
  rootStatus: RootStatus,
  onSelectMode: (PrivilegeMode) -> Unit,
  onRequestShizuku: () -> Unit,
  onRefreshChecks: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val canWriteSettings = Settings.System.canWrite(context)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.AdminPanelSettings,
          contentDescription = null,
          tint = ElectricCyan,
        )
        Spacer(Modifier.width(10.dp))
        Text("On-Device Execution Engine", style = MaterialTheme.typography.titleLarge)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            "GameTweaks by Blaze00 (TG @Blaze00) — every tweak applies directly on your phone. Auto mode uses in-app APIs and automatically upgrades to Shizuku or Root when active.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PrivilegeOptionCard(
          title = "Auto (In-App + Shizuku/Root)",
          subtitle = "Applies settings automatically using the highest privilege available on device",
          selected = activeMode == PrivilegeMode.STANDARD,
          statusText = "Recommended",
          accent = ElectricCyan,
          onClick = { onSelectMode(PrivilegeMode.STANDARD) },
          tag = "select_mode_standard",
        )

        val shizukuText =
          when (shizukuStatus) {
            is ShizukuStatus.Ready -> "Connected (v${shizukuStatus.version}) • UID 2000 active"
            ShizukuStatus.PermissionRequired -> "Running — tap below to grant permission"
            ShizukuStatus.BindingService -> "Binding UserService..."
            ShizukuStatus.NotRunning -> "Installed, Shizuku service not started"
            ShizukuStatus.NotInstalled -> "Shizuku app not installed"
          }
        PrivilegeOptionCard(
          title = "Shizuku (System On-Device)",
          subtitle = shizukuText,
          selected = activeMode == PrivilegeMode.SHIZUKU,
          statusText = if (shizukuStatus is ShizukuStatus.Ready) "Ready" else "Setup",
          accent = ShizukuIndigo,
          onClick = { onSelectMode(PrivilegeMode.SHIZUKU) },
          tag = "select_mode_shizuku",
        )

        if (shizukuStatus is ShizukuStatus.PermissionRequired) {
          FilledTonalButton(
            onClick = onRequestShizuku,
            modifier = Modifier.fillMaxWidth().testTag("grant_shizuku_btn"),
          ) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Grant Shizuku Permission")
          }
        }

        val rootText =
          when (rootStatus) {
            is RootStatus.Available -> "su verified at ${rootStatus.suPath}"
            RootStatus.Checking -> "Probing su binary..."
            RootStatus.Unavailable -> "su not detected on this device"
          }
        PrivilegeOptionCard(
          title = "Root Superuser (su UID 0)",
          subtitle = rootText,
          selected = activeMode == PrivilegeMode.ROOT,
          statusText = if (rootStatus is RootStatus.Available) "Granted" else "Unavailable",
          accent = RootCrimson,
          onClick = { onSelectMode(PrivilegeMode.ROOT) },
          tag = "select_mode_root",
        )

        if (!canWriteSettings) {
          FilledTonalButton(
            onClick = {
              val intent =
                Intent(
                  Settings.ACTION_MANAGE_WRITE_SETTINGS,
                  Uri.parse("package:${context.packageName}"),
                )
              context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().testTag("grant_write_settings_btn"),
          ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Allow Modify System Settings")
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss, modifier = Modifier.testTag("close_privilege_dialog")) {
        Text("Done")
      }
    },
    dismissButton = {
      TextButton(onClick = onRefreshChecks, modifier = Modifier.testTag("recheck_privileges_btn")) {
        Text("Re-check")
      }
    },
  )
}

@Composable
private fun PrivilegeOptionCard(
  title: String,
  subtitle: String,
  selected: Boolean,
  statusText: String,
  accent: Color,
  onClick: () -> Unit,
  tag: String,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color =
      if (selected) accent.copy(alpha = 0.14f)
      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    border =
      BorderStroke(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
      ),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag(tag),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
          )
        }
        Spacer(Modifier.height(3.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (selected) {
        Icon(
          imageVector = Icons.Rounded.CheckCircle,
          contentDescription = "Selected",
          tint = accent,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}
