package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.PulseTuneViewModel
import com.example.ui.components.PrivilegeEngineDialog
import com.example.ui.components.PrivilegeStatusBadge
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DisplayProfilesScreen
import com.example.ui.screens.GpuAngleScreen
import com.example.ui.screens.TweaksConsoleScreen
import com.example.ui.theme.PulseTuneTheme
import kotlin.math.roundToInt

private enum class PulseDestination(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  DASHBOARD("dashboard", "Monitor", Icons.Rounded.Speed, Icons.Outlined.Speed),
  DISPLAY_PROFILES(
    "display_profiles",
    "Hz & FPS",
    Icons.Rounded.DisplaySettings,
    Icons.Outlined.DisplaySettings,
  ),
  GPU_ANGLE("gpu_angle", "GPU & ANGLE", Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq),
  TWEAKS("tweaks_console", "Tweaks", Icons.Rounded.Tune, Icons.Outlined.Tune),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { PulseTuneRoot() }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseTuneRoot(vm: PulseTuneViewModel = viewModel()) {
  val state by vm.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val snackbarHostState = remember { SnackbarHostState() }
  val navController = rememberNavController()
  var showPrivilegeDialog by remember { mutableStateOf(false) }
  var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

  val notifPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      if (Settings.canDrawOverlays(context)) {
        vm.startOverlay()
      }
    }

  val startOverlayWithPerms: () -> Unit = {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      vm.startOverlay()
    }
  }

  val overlaySettingsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      val granted = Settings.canDrawOverlays(context)
      hasOverlayPermission = granted
      if (granted) {
        startOverlayWithPerms()
      }
    }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(vm.snackbars) {
    vm.snackbars.collect { msg -> snackbarHostState.showSnackbar(msg) }
  }

  // Sync window preferredDisplayModeId with targetHz when supported on hardware
  LaunchedEffect(state.settings.targetHz) {
    applyWindowRefreshRateHint(context, state.settings.targetHz)
  }

  PulseTuneTheme(darkTheme = state.settings.darkTheme) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateTo: (String) -> Unit = { route ->
      navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useRail = maxWidth >= 600.dp

      Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
          SnackbarHost(hostState = snackbarHostState, modifier = Modifier.navigationBarsPadding())
        },
        topBar = {
          TopAppBar(
            title = {
              androidx.compose.foundation.layout.Column {
                Text(
                  text = "GameTweaks",
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "by Blaze00 • TG @Blaze00",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            },
            actions = {
              PrivilegeStatusBadge(
                activeMode = state.settings.privilegeMode,
                shizukuStatus = state.shizukuStatus,
                rootStatus = state.rootStatus,
                onClick = { showPrivilegeDialog = true },
              )
              Spacer(Modifier.width(4.dp))
              IconButton(
                onClick = vm::toggleDarkTheme,
                modifier = Modifier.testTag("toggle_theme_btn"),
              ) {
                Icon(
                  imageVector =
                    if (state.settings.darkTheme) Icons.Rounded.LightMode
                    else Icons.Rounded.DarkMode,
                  contentDescription = "Toggle theme",
                )
              }
            },
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
              ),
          )
        },
        bottomBar = {
          if (!useRail) {
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              tonalElevation = 4.dp,
            ) {
              PulseDestination.entries.forEach { dest ->
                val selected =
                  currentDestination?.hierarchy?.any { it.route == dest.route } == true
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateTo(dest.route) },
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = dest.label,
                    )
                  },
                  label = { Text(dest.label) },
                  modifier = Modifier.testTag("nav_${dest.route}"),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
          if (useRail) {
            NavigationRail(
              containerColor = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxHeight().padding(top = innerPadding.calculateTopPadding()),
            ) {
              PulseDestination.entries.forEach { dest ->
                val selected =
                  currentDestination?.hierarchy?.any { it.route == dest.route } == true
                NavigationRailItem(
                  selected = selected,
                  onClick = { navigateTo(dest.route) },
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = dest.label,
                    )
                  },
                  label = { Text(dest.label) },
                  modifier = Modifier.testTag("rail_${dest.route}"),
                )
              }
            }
          }

          NavHost(
            navController = navController,
            startDestination = PulseDestination.DASHBOARD.route,
            modifier = Modifier.weight(1f).fillMaxSize(),
          ) {
            composable(PulseDestination.DASHBOARD.route) {
              DashboardScreen(
                state = state,
                hasOverlayPermission = hasOverlayPermission,
                onQuickRamBoost = vm::triggerQuickRamBoost,
                onSmoothTouchBoost = vm::triggerSmoothTouchBoost,
                onSuperSmoothBoost = vm::triggerSuperSmoothBoost,
                onToggleOverlay = {
                  when {
                    state.isOverlayRunning -> vm.stopOverlay()
                    hasOverlayPermission -> startOverlayWithPerms()
                    else -> {
                      val intent =
                        Intent(
                          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                          Uri.parse("package:${context.packageName}"),
                        )
                      overlaySettingsLauncher.launch(intent)
                    }
                  }
                },
                onOverlayConfigChange = vm::updateOverlayConfig,
                onResetOverlayPosition = vm::resetOverlayPosition,
                onApplyProfile = vm::applyProfile,
                onApplyHz = vm::applyRefreshRate,
                onOpenPrivilegeDialog = { showPrivilegeDialog = true },
                contentPadding = innerPadding,
              )
            }
            composable(PulseDestination.DISPLAY_PROFILES.route) {
              DisplayProfilesScreen(
                state = state,
                onApplyHz = vm::applyRefreshRate,
                onApplyFpsLock = vm::applyFpsLock,
                onTargetPackageChange = vm::updateTargetPackage,
                onLowEndFpsOptimizeChange = vm::setLowEndFpsOptimize,
                onRemoveAppFpsRule = vm::removeAppFpsRule,
                onApplyAllAppFpsRules = vm::applyAllAppFpsRules,
                onApplyProfile = vm::applyProfile,
                onQuickRamBoost = vm::triggerQuickRamBoost,
                contentPadding = innerPadding,
              )
            }
            composable(PulseDestination.GPU_ANGLE.route) {
              GpuAngleScreen(
                state = state,
                onSaveAngle = vm::saveAndApplyAngle,
                onSaveGpu = vm::saveAndApplyGpu,
                onApplyPreset = vm::applyGpuPreset,
                contentPadding = innerPadding,
              )
            }
            composable(PulseDestination.TWEAKS.route) {
              TweaksConsoleScreen(
                state = state,
                onRunTweak = vm::runTweak,
                onApplyRootTuning = vm::applyRootKernelTuning,
                onApplyChipsetBoost = vm::applyChipsetBoost,
                onRunManualCommand = vm::runManualShellCommand,
                onClearLogs = vm::clearLogs,
                contentPadding = innerPadding,
              )
            }
          }
        }
      }
    }

    if (showPrivilegeDialog) {
      PrivilegeEngineDialog(
        activeMode = state.settings.privilegeMode,
        shizukuStatus = state.shizukuStatus,
        rootStatus = state.rootStatus,
        onSelectMode = vm::setPrivilegeMode,
        onRequestShizuku = vm::requestShizukuPermission,
        onRefreshChecks = vm::refreshPrivilegeChecks,
        onDismiss = { showPrivilegeDialog = false },
      )
    }
  }
}

private fun applyWindowRefreshRateHint(context: Context, targetHz: Int) {
  val activity = context as? Activity ?: return
  val window = activity.window ?: return
  val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
  val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
  val attrs = window.attributes
  if (targetHz <= 0) {
    attrs.preferredDisplayModeId = 0
    attrs.preferredRefreshRate = 0f
  } else {
    val activeMode = display.mode
    val sameResModes =
      display.supportedModes.filter {
        it.physicalWidth == activeMode.physicalWidth &&
          it.physicalHeight == activeMode.physicalHeight
      }
    val candidates = sameResModes.ifEmpty { display.supportedModes.toList() }
    val matchingMode =
      candidates.minByOrNull { kotlin.math.abs(it.refreshRate.roundToInt() - targetHz) }
    if (matchingMode != null) {
      attrs.preferredDisplayModeId = matchingMode.modeId
    }
    attrs.preferredRefreshRate = targetHz.toFloat()
  }
  window.attributes = attrs
}
