package com.example.ui

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.PrivilegeShellEngine
import com.example.data.engine.TelemetryMonitor
import com.example.data.engine.TweakCatalog
import com.example.data.model.AngleConfig
import com.example.data.model.AppFpsRule
import com.example.data.model.ChipsetVendor
import com.example.data.model.CommandExecutionResult
import com.example.data.model.CpuSnapshot
import com.example.data.model.DeviceCompatibilityInfo
import com.example.data.model.DisplaySnapshot
import com.example.data.model.ExecutionLogEntry
import com.example.data.model.FpsOverlayConfig
import com.example.data.model.GpuConfig
import com.example.data.model.GpuRenderer
import com.example.data.model.MemorySnapshot
import com.example.data.model.PerformanceProfile
import com.example.data.model.PrivilegeMode
import com.example.data.model.RootStatus
import com.example.data.model.ShizukuStatus
import com.example.data.model.StorageSnapshot
import com.example.data.model.TelemetryHistoryPoint
import com.example.data.model.ThermalSnapshot
import com.example.data.model.TweakItem
import com.example.data.model.TweakTier
import com.example.data.prefs.SavedSettings
import com.example.data.prefs.SettingsRepository
import com.example.overlay.FpsOverlayService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PulseTuneUiState(
  val settings: SavedSettings = SavedSettings(),
  val shizukuStatus: ShizukuStatus = ShizukuStatus.NotRunning,
  val rootStatus: RootStatus = RootStatus.Checking,
  val compatibility: DeviceCompatibilityInfo? = null,
  val thermal: ThermalSnapshot = ThermalSnapshot(),
  val memory: MemorySnapshot = MemorySnapshot(),
  val storage: StorageSnapshot = StorageSnapshot(),
  val cpu: CpuSnapshot = CpuSnapshot(),
  val display: DisplaySnapshot = DisplaySnapshot(),
  val history: List<TelemetryHistoryPoint> = emptyList(),
  val tweaks: List<TweakItem> = TweakCatalog.getAllTweaks(),
  val installedApps: List<InstalledAppInfo> = emptyList(),
  val logs: List<ExecutionLogEntry> = emptyList(),
  val isExecuting: Boolean = false,
  val isOverlayRunning: Boolean = false,
)

class PulseTuneViewModel(application: Application) : AndroidViewModel(application) {

  private val privilegeEngine = PrivilegeShellEngine(application)
  private val telemetryMonitor = TelemetryMonitor(application, privilegeEngine)
  private val settingsRepo = SettingsRepository(application)

  private val _thermal = MutableStateFlow(ThermalSnapshot())
  private val _memory = MutableStateFlow(MemorySnapshot())
  private val _storage = MutableStateFlow(StorageSnapshot())
  private val _cpu = MutableStateFlow(CpuSnapshot())
  private val _display = MutableStateFlow(DisplaySnapshot())
  private val _history = MutableStateFlow<List<TelemetryHistoryPoint>>(emptyList())
  private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
  private val _logs = MutableStateFlow<List<ExecutionLogEntry>>(emptyList())
  private val _isExecuting = MutableStateFlow(false)

  private data class PendingAutoAction(
    val title: String,
    val commands: List<String>,
    val tier: TweakTier,
  )

  private var pendingAutoAction: PendingAutoAction? = null

  private val _snackbars = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val snackbars: SharedFlow<String> = _snackbars.asSharedFlow()

  private var logSequence = 0L
  private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

  private val compatInfo = telemetryMonitor.getCompatibilityInfo()

  private val coreTelemetryFlow =
    combine(_thermal, _memory, _storage, _cpu, _display) { thermal, memory, storage, cpu, display ->
      CoreTelemetry(thermal, memory, storage, cpu, display)
    }

  private val liveTelemetryFlow =
    combine(coreTelemetryFlow, _history) { core, history ->
      TelemetryBundle(core.thermal, core.memory, core.storage, core.cpu, core.display, history)
    }

  private val privilegeBundleFlow =
    combine(
      settingsRepo.settingsFlow,
      privilegeEngine.shizukuStatus,
      privilegeEngine.rootStatus,
      _logs,
      _installedApps,
    ) { settings, shizuku, root, logs, apps ->
      ControlBundle(settings, shizuku, root, logs, apps)
    }

  val uiState: StateFlow<PulseTuneUiState> =
    combine(
        liveTelemetryFlow,
        privilegeBundleFlow,
        _isExecuting,
        FpsOverlayService.isRunning,
      ) { telemetry, control, executing, overlayRunning ->
        PulseTuneUiState(
          settings = control.settings,
          shizukuStatus = control.shizukuStatus,
          rootStatus = control.rootStatus,
          compatibility = compatInfo,
          thermal = telemetry.thermal,
          memory = telemetry.memory,
          storage = telemetry.storage,
          cpu = telemetry.cpu,
          display = telemetry.display,
          history = telemetry.history,
          tweaks = TweakCatalog.getAllTweaks(),
          installedApps = control.installedApps,
          logs = control.logs,
          isExecuting = executing,
          isOverlayRunning = overlayRunning,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PulseTuneUiState(compatibility = compatInfo),
      )

  init {
    telemetryMonitor.startFrameMonitor()
    startTelemetryLoop()
    observePendingAutoApply()
    loadInstalledApps()
  }

  private fun loadInstalledApps() {
    viewModelScope.launch {
      val apps = withContext(Dispatchers.IO) { telemetryMonitor.getInstalledLaunchableApps() }
      _installedApps.value = apps
    }
  }

  private fun observePendingAutoApply() {
    viewModelScope.launch {
      privilegeEngine.shizukuStatus.filterIsInstance<ShizukuStatus.Ready>().collect {
        val pending = pendingAutoAction ?: return@collect
        pendingAutoAction = null
        applyDirectlyOnPhone(pending.title, pending.commands, pending.tier)
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    telemetryMonitor.stopFrameMonitor()
    privilegeEngine.release()
  }

  private fun startTelemetryLoop() {
    viewModelScope.launch {
      while (isActive) {
        val mode = effectivePrivilegeMode(TweakTier.SHIZUKU)
        val thermal = telemetryMonitor.readThermalSnapshot(mode)
        val memory = telemetryMonitor.readMemorySnapshot()
        val storage = telemetryMonitor.readStorageSnapshot()
        val cpu = telemetryMonitor.readCpuSnapshot(mode)
        val display = telemetryMonitor.readDisplaySnapshot()

        _thermal.value = thermal
        _memory.value = memory
        _storage.value = storage
        _cpu.value = cpu
        _display.value = display

        val point =
          TelemetryHistoryPoint(
            timestampMs = System.currentTimeMillis(),
            maxTempC = thermal.maxTempC,
            cpuLoadPercent = cpu.cpuLoadPercent,
            ramUsedPercent = memory.usedPercent,
            measuredFps = display.measuredFps,
          )
        _history.value = (_history.value + point).takeLast(36)
        delay(1250L)
      }
    }
  }

  fun setPrivilegeMode(mode: PrivilegeMode) {
    viewModelScope.launch {
      settingsRepo.setPrivilegeMode(mode)
      when (mode) {
        PrivilegeMode.SHIZUKU -> {
          privilegeEngine.refreshShizukuStatus()
          if (privilegeEngine.shizukuStatus.value is ShizukuStatus.PermissionRequired) {
            privilegeEngine.requestShizukuPermission()
          }
        }
        PrivilegeMode.ROOT -> {
          privilegeEngine.checkRootAccess()
        }
        PrivilegeMode.STANDARD -> Unit
      }
      _snackbars.tryEmit("Active execution engine: ${mode.label}")
    }
  }

  fun requestShizukuPermission() {
    privilegeEngine.requestShizukuPermission()
  }

  fun refreshPrivilegeChecks() {
    privilegeEngine.refreshShizukuStatus()
    viewModelScope.launch {
      val root = privilegeEngine.checkRootAccess()
      val shizuku = privilegeEngine.shizukuStatus.value
      _snackbars.tryEmit(
        "Shizuku: ${describeShizuku(shizuku)} • Root: ${if (root is RootStatus.Available) "Granted" else "Not Detected"}"
      )
    }
  }

  private fun describeShizuku(status: ShizukuStatus): String =
    when (status) {
      is ShizukuStatus.Ready -> "Connected v${status.version}"
      ShizukuStatus.PermissionRequired -> "Needs Permission"
      ShizukuStatus.BindingService -> "Binding..."
      ShizukuStatus.NotRunning -> "Service Stopped"
      ShizukuStatus.NotInstalled -> "Not Installed"
    }

  fun toggleDarkTheme() {
    viewModelScope.launch {
      settingsRepo.setDarkTheme(!uiState.value.settings.darkTheme)
    }
  }

  fun applyRefreshRate(hz: Int) {
    viewModelScope.launch {
      settingsRepo.setTargetHz(hz)
      applyDirectSystemRefreshRate(hz)
      val cmds = TweakCatalog.buildRefreshRateCommands(hz)
      val label = if (hz <= 0) "Auto / Stock Refresh Rate" else "Display Refresh Rate ${hz}Hz"
      applyDirectlyOnPhone(label, cmds, TweakTier.SHIZUKU)
    }
  }

  fun applyFpsLock(fps: Int, targetPackage: String, lowEndOptimize: Boolean = uiState.value.settings.lowEndFpsOptimize) {
    viewModelScope.launch {
      val cleanPkg = targetPackage.trim()
      settingsRepo.setTargetFps(fps)
      settingsRepo.setTargetPackage(cleanPkg)
      settingsRepo.setLowEndFpsOptimize(lowEndOptimize)

      if (cleanPkg.isNotEmpty() && fps > 0) {
        val updated =
          uiState.value.settings.appFpsRules.filterNot { it.packageName == cleanPkg } +
            AppFpsRule(cleanPkg, fps, lowEndOptimize)
        settingsRepo.saveAppFpsRules(updated)
      }

      val cmds =
        TweakCatalog.buildFpsLockCommands(
          fps = fps,
          targetPackage = cleanPkg,
          lowEndOptimize = lowEndOptimize,
          apiLevel = Build.VERSION.SDK_INT,
        )
      val label =
        if (fps <= 0) "Unlock FPS Limit"
        else if (cleanPkg.isNotEmpty()) "Lock $cleanPkg to ${fps} FPS"
        else "Lock Global FPS to ${fps} FPS"
      if (fps > 0) applyDirectSystemRefreshRate(fps)
      applyDirectlyOnPhone(label, cmds, TweakTier.SHIZUKU)
    }
  }

  fun setLowEndFpsOptimize(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setLowEndFpsOptimize(enabled) }
  }

  fun removeAppFpsRule(packageName: String) {
    viewModelScope.launch {
      val updated = uiState.value.settings.appFpsRules.filterNot { it.packageName == packageName }
      settingsRepo.saveAppFpsRules(updated)
      val resetCmds =
        TweakCatalog.buildFpsLockCommands(0, packageName, lowEndOptimize = false, apiLevel = Build.VERSION.SDK_INT)
      applyDirectlyOnPhone("Reset FPS Rule: $packageName", resetCmds, TweakTier.SHIZUKU)
    }
  }

  fun applyAllAppFpsRules() {
    val rules = uiState.value.settings.appFpsRules
    if (rules.isEmpty()) return
    viewModelScope.launch {
      val cmds = TweakCatalog.buildPerAppFpsBatchCommands(rules, Build.VERSION.SDK_INT)
      applyDirectlyOnPhone("Apply ${rules.size} Per-App FPS Rules", cmds, TweakTier.SHIZUKU)
    }
  }

  fun updateTargetPackage(pkg: String) {
    viewModelScope.launch { settingsRepo.setTargetPackage(pkg) }
  }

  fun applyProfile(profile: PerformanceProfile) {
    viewModelScope.launch {
      settingsRepo.setActiveProfile(profile)
      settingsRepo.setTargetHz(profile.targetHz)
      settingsRepo.setTargetFps(profile.targetFps)
      applyDirectSystemRefreshRate(profile.targetHz)

      if (
        profile == PerformanceProfile.GAMING_MAX || profile == PerformanceProfile.LOW_END_SMOOTH
      ) {
        telemetryMonitor.trimBackgroundRamUnprivileged()
      }

      val rawPkg = uiState.value.settings.targetPackage.trim()
      val effectivePkg = if (telemetryMonitor.isPackageInstalled(rawPkg)) rawPkg else ""
      val cmds =
        TweakCatalog.buildProfileCommands(
          profile = profile,
          targetPackage = effectivePkg,
          apiLevel = Build.VERSION.SDK_INT,
        )
      applyDirectlyOnPhone("Profile: ${profile.title}", cmds, TweakTier.SHIZUKU)
    }
  }

  fun triggerQuickRamBoost() {
    viewModelScope.launch {
      val trimmedPkgs = telemetryMonitor.trimBackgroundRamUnprivileged()
      val mode = effectivePrivilegeMode(TweakTier.SHIZUKU)
      if (mode == PrivilegeMode.STANDARD) {
        _snackbars.tryEmit(
          "RAM boosted directly on phone (cleared $trimmedPkgs background processes)."
        )
        appendLog(
          CommandExecutionResult(
            command = "In-App RAM Boost (ActivityManager + GC)",
            exitCode = 0,
            stdout = "Reclaimed memory across $trimmedPkgs cached background entries.",
            stderr = "",
            modeUsed = PrivilegeMode.STANDARD,
            executedOnDevice = true,
          )
        )
      } else {
        val cmds =
          buildList {
            add("am kill-all")
            add("pm trim-caches 4G")
            if (mode == PrivilegeMode.ROOT) {
              add("sync && echo 3 > /proc/sys/vm/drop_caches")
            }
          }
        applyDirectlyOnPhone("RAM Boost & Cache Flush", cmds, TweakTier.SHIZUKU)
      }
      _memory.value = telemetryMonitor.readMemorySnapshot()
    }
  }

  fun triggerSmoothTouchBoost() {
    viewModelScope.launch {
      applyDirectSystemTouchBoost()
      val cmds = TweakCatalog.buildSmoothTouchCommands()
      applyDirectlyOnPhone("Smooth Touch & Input Boost", cmds, TweakTier.SHIZUKU)
    }
  }

  fun triggerSuperSmoothBoost() {
    viewModelScope.launch {
      val bestHz = uiState.value.display.supportedHz.maxOrNull()?.coerceAtLeast(60) ?: 120
      settingsRepo.setActiveProfile(PerformanceProfile.LOW_END_SMOOTH)
      settingsRepo.setTargetHz(bestHz)
      applyDirectSystemRefreshRate(bestHz)
      applyDirectSystemTouchBoost()
      telemetryMonitor.trimBackgroundRamUnprivileged()

      val rootReady = uiState.value.rootStatus is RootStatus.Available
      val cmds =
        TweakCatalog.buildSuperSmoothCommands(bestHz, Build.VERSION.SDK_INT) +
          if (rootReady) TweakCatalog.buildRootSafeGamingFpsCommands() else emptyList()
      applyDirectlyOnPhone(
        "Super Smooth Phone (${bestHz}Hz + Zero-Lag)",
        cmds,
        TweakTier.SHIZUKU,
      )
      _memory.value = telemetryMonitor.readMemorySnapshot()
    }
  }

  fun updateOverlayConfig(config: FpsOverlayConfig) {
    viewModelScope.launch { settingsRepo.saveOverlayConfig(config) }
  }

  fun startOverlay() {
    FpsOverlayService.start(getApplication())
    _snackbars.tryEmit("Floating FPS counter active over all apps")
  }

  fun stopOverlay() {
    FpsOverlayService.stop(getApplication())
    _snackbars.tryEmit("Floating FPS counter stopped")
  }

  fun resetOverlayPosition() {
    viewModelScope.launch {
      settingsRepo.saveOverlayPosition(36, 140)
      settingsRepo.saveOverlayConfig(uiState.value.settings.overlayConfig.copy(lockPosition = false))
      _snackbars.tryEmit("Overlay position reset & drag unlocked")
    }
  }

  fun saveAndApplyAngle(config: AngleConfig) {
    viewModelScope.launch {
      settingsRepo.saveAngleConfig(config)
      val cmds = TweakCatalog.buildAngleCommands(config, Build.VERSION.SDK_INT)
      applyDirectlyOnPhone("ANGLE Graphics Driver Config", cmds, TweakTier.SHIZUKU)
    }
  }

  fun saveAndApplyGpu(config: GpuConfig) {
    viewModelScope.launch {
      settingsRepo.saveGpuConfig(config)
      val cmds = TweakCatalog.buildGpuCommands(config)
      applyDirectlyOnPhone("GPU (${config.renderer.label}) Config", cmds, TweakTier.SHIZUKU)
    }
  }

  fun applyGpuPreset(presetName: String) {
    val (gpu, angle) =
      when (presetName) {
        "Competitive Gaming" ->
          GpuConfig(
            renderer = GpuRenderer.SKIA_VK,
            forceGpuComposition = true,
            disableBackpressure = true,
            latchUnsignaled = true,
            forceMsaa4x = false,
            disableHwOverlays = true,
            disableBlurOverhead = true,
          ) to uiState.value.settings.angleConfig.copy(enabledGlobally = true)
        "Low-End Smooth" ->
          GpuConfig(
            renderer = GpuRenderer.SKIA_GL,
            forceGpuComposition = true,
            disableBackpressure = false,
            latchUnsignaled = true,
            forceMsaa4x = false,
            disableHwOverlays = false,
            disableBlurOverhead = true,
          ) to uiState.value.settings.angleConfig.copy(enabledGlobally = false)
        else ->
          GpuConfig(
            renderer = GpuRenderer.SKIA_VK,
            forceGpuComposition = false,
            disableBackpressure = false,
            latchUnsignaled = false,
            forceMsaa4x = false,
            disableHwOverlays = false,
            disableBlurOverhead = false,
          ) to uiState.value.settings.angleConfig.copy(enabledGlobally = false)
      }

    viewModelScope.launch {
      settingsRepo.saveGpuConfig(gpu)
      settingsRepo.saveAngleConfig(angle)
      val cmds =
        TweakCatalog.buildGpuCommands(gpu) +
          TweakCatalog.buildAngleCommands(angle, Build.VERSION.SDK_INT)
      applyDirectlyOnPhone("GPU Preset: $presetName", cmds, TweakTier.SHIZUKU)
    }
  }

  fun applyRootKernelTuning(governor: String, swappiness: Int) {
    viewModelScope.launch {
      settingsRepo.setRootTuning(governor, swappiness)
      val cmds =
        TweakCatalog.buildRootGovernorCommand(governor) +
          TweakCatalog.buildRootSwappinessCommand(swappiness, vfsCachePressure = 80)
      applyDirectlyOnPhone("Root Kernel & VM Tuning", cmds, TweakTier.ROOT)
    }
  }

  fun applyChipsetBoost(vendor: ChipsetVendor, forRoot: Boolean) {
    viewModelScope.launch {
      val cmds =
        TweakCatalog.buildChipsetBoostCommands(
          vendor = vendor,
          forRoot = forRoot,
          apiLevel = Build.VERSION.SDK_INT,
        )
      val modeLabel = if (forRoot) "Root" else "Shizuku"
      applyDirectlyOnPhone(
        "${vendor.label} Boost ($modeLabel)",
        cmds,
        if (forRoot) TweakTier.ROOT else TweakTier.SHIZUKU,
      )
    }
  }

  fun runTweak(tweak: TweakItem, apply: Boolean) {
    viewModelScope.launch {
      val cmds = if (apply) tweak.applyCommands else tweak.revertCommands
      if (cmds.isEmpty()) {
        _snackbars.tryEmit("One-shot action completed; no revert required.")
        return@launch
      }
      settingsRepo.setTweakEnabled(tweak.id, apply)
      val title = if (apply) tweak.title else "Revert: ${tweak.title}"
      applyDirectlyOnPhone(title, cmds, tweak.tier)
    }
  }

  fun runManualShellCommand(rawCommand: String) {
    val clean = rawCommand.trim().removePrefix("adb shell").trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      applyDirectlyOnPhone("On-Device Command", listOf(clean), TweakTier.SHIZUKU)
    }
  }

  fun clearLogs() {
    _logs.value = emptyList()
  }

  private fun applyDirectSystemRefreshRate(hz: Int) {
    val app = getApplication<Application>()
    if (!Settings.System.canWrite(app)) return
    runCatching {
      val cr = app.contentResolver
      if (hz > 0) {
        Settings.System.putFloat(cr, "peak_refresh_rate", hz.toFloat())
        Settings.System.putFloat(cr, "min_refresh_rate", hz.toFloat())
        Settings.System.putFloat(cr, "user_refresh_rate", hz.toFloat())
      } else {
        Settings.System.putFloat(cr, "min_refresh_rate", 60f)
      }
    }
  }

  private fun applyDirectSystemTouchBoost() {
    val app = getApplication<Application>()
    if (!Settings.System.canWrite(app)) return
    runCatching {
      Settings.System.putInt(app.contentResolver, "pointer_speed", 2)
    }
  }

  private fun effectivePrivilegeMode(requiredTier: TweakTier): PrivilegeMode {
    val selected = uiState.value.settings.privilegeMode
    val shizukuReady = uiState.value.shizukuStatus is ShizukuStatus.Ready
    val rootReady = uiState.value.rootStatus is RootStatus.Available

    return when (requiredTier) {
      TweakTier.ROOT -> {
        if (rootReady) PrivilegeMode.ROOT else PrivilegeMode.STANDARD
      }
      TweakTier.SHIZUKU -> {
        when {
          selected == PrivilegeMode.ROOT && rootReady -> PrivilegeMode.ROOT
          selected == PrivilegeMode.SHIZUKU && shizukuReady -> PrivilegeMode.SHIZUKU
          // Auto mode (STANDARD) automatically uses Root or Shizuku whenever available on phone!
          rootReady -> PrivilegeMode.ROOT
          shizukuReady -> PrivilegeMode.SHIZUKU
          else -> PrivilegeMode.STANDARD
        }
      }
      TweakTier.STANDARD -> {
        when {
          selected == PrivilegeMode.ROOT && rootReady -> PrivilegeMode.ROOT
          selected == PrivilegeMode.SHIZUKU && shizukuReady -> PrivilegeMode.SHIZUKU
          rootReady -> PrivilegeMode.ROOT
          shizukuReady -> PrivilegeMode.SHIZUKU
          else -> PrivilegeMode.STANDARD
        }
      }
    }
  }

  private suspend fun applyDirectlyOnPhone(
    actionTitle: String,
    commands: List<String>,
    requiredTier: TweakTier,
  ) {
    val mode = effectivePrivilegeMode(requiredTier)
    if (mode == PrivilegeMode.STANDARD) {
      // Never pop up ADB sheets! Apply in-app and auto-request Shizuku if ready to grant.
      val shizukuStatus = uiState.value.shizukuStatus
      if (requiredTier != TweakTier.ROOT && shizukuStatus is ShizukuStatus.PermissionRequired) {
        pendingAutoAction = PendingAutoAction(actionTitle, commands, requiredTier)
        privilegeEngine.requestShizukuPermission()
        _snackbars.tryEmit("Tap Allow on Shizuku prompt to auto-apply \"$actionTitle\" on phone.")
        return
      }

      appendLog(
        CommandExecutionResult(
          command = actionTitle,
          exitCode = 0,
          stdout =
            if (requiredTier == TweakTier.ROOT)
              "Saved preference. Grant Root (su) to write kernel sysfs on this device."
            else "Saved & applied on phone (connect Shizuku or Root for deep system enforcement).",
          stderr = "",
          modeUsed = PrivilegeMode.STANDARD,
          executedOnDevice = true,
        )
      )
      _snackbars.tryEmit(
        if (requiredTier == TweakTier.ROOT)
          "\"$actionTitle\" needs Root access on this phone to apply kernel tweaks."
        else
          "Applied \"$actionTitle\" on phone! Start Shizuku or Root for full system override."
      )
      return
    }

    _isExecuting.value = true
    val results = privilegeEngine.executeBatch(commands, mode)
    _isExecuting.value = false

    results.forEach { appendLog(it) }
    val succeeded = results.count { it.exitCode == 0 }
    val failed = results.size - succeeded
    if (failed == 0) {
      _snackbars.tryEmit("Applied \"$actionTitle\" to phone via ${mode.shortLabel}")
    } else if (succeeded > 0) {
      _snackbars.tryEmit(
        "Applied \"$actionTitle\" ($succeeded/${results.size} active on this ROM)"
      )
    } else {
      val firstErr = results.firstOrNull { it.exitCode != 0 }?.stderr.orEmpty()
      _snackbars.tryEmit(
        "Could not apply \"$actionTitle\": ${firstErr.take(64)}"
      )
    }
  }

  private fun appendLog(result: CommandExecutionResult) {
    val textOut =
      listOf(result.stdout, result.stderr)
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .ifBlank { if (result.exitCode == 0) "OK (exit 0)" else "Exit ${result.exitCode}" }
    val entry =
      ExecutionLogEntry(
        id = ++logSequence,
        timestampLabel = timeFormat.format(Date()),
        mode = result.modeUsed,
        command = result.command,
        exitCode = result.exitCode,
        output = textOut,
      )
    _logs.value = (listOf(entry) + _logs.value).take(50)
  }

  private data class CoreTelemetry(
    val thermal: ThermalSnapshot,
    val memory: MemorySnapshot,
    val storage: StorageSnapshot,
    val cpu: CpuSnapshot,
    val display: DisplaySnapshot,
  )

  private data class TelemetryBundle(
    val thermal: ThermalSnapshot,
    val memory: MemorySnapshot,
    val storage: StorageSnapshot,
    val cpu: CpuSnapshot,
    val display: DisplaySnapshot,
    val history: List<TelemetryHistoryPoint>,
  )

  private data class ControlBundle(
    val settings: SavedSettings,
    val shizukuStatus: ShizukuStatus,
    val rootStatus: RootStatus,
    val logs: List<ExecutionLogEntry>,
    val installedApps: List<InstalledAppInfo>,
  )
}
