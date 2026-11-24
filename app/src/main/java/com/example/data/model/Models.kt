package com.example.data.model

enum class PrivilegeMode(val label: String, val shortLabel: String) {
  STANDARD("Auto (In-App + Detect)", "AUTO"),
  SHIZUKU("Shizuku (UID 2000)", "SHIZUKU"),
  ROOT("Root (su UID 0)", "ROOT"),
}

sealed interface ShizukuStatus {
  data object NotInstalled : ShizukuStatus

  data object NotRunning : ShizukuStatus

  data object PermissionRequired : ShizukuStatus

  data object BindingService : ShizukuStatus

  data class Ready(val version: Int) : ShizukuStatus
}

sealed interface RootStatus {
  data object Checking : RootStatus

  data class Available(val suPath: String) : RootStatus

  data object Unavailable : RootStatus
}

enum class TweakTier(val badge: String) {
  STANDARD("IN-APP"),
  SHIZUKU("SHIZUKU"),
  ROOT("ROOT ONLY"),
}

enum class TweakCategory(val label: String) {
  DISPLAY_FPS("Hz & FPS"),
  PROFILES("Profiles"),
  LOW_END("Low-End Boost"),
  GPU_ANGLE("GPU & ANGLE"),
  KERNEL_ROOT("Kernel & Sysfs"),
}

enum class PerformanceProfile(
  val id: String,
  val title: String,
  val subtitle: String,
  val targetHz: Int,
  val targetFps: Int,
) {
  LOW_END_SMOOTH(
    id = "low_end_smooth",
    title = "Low-End Smoothness",
    subtitle = "0.5x animations, app freezer, cache trim & ART speed-profile",
    targetHz = 60,
    targetFps = 60,
  ),
  GAMING_MAX(
    id = "gaming_max",
    title = "Gaming Max + RAM Boost",
    subtitle = "Fixed performance mode, background trim, Vulkan/GPU priority",
    targetHz = 120,
    targetFps = 120,
  ),
  BATTERY_SAVER(
    id = "battery_saver",
    title = "Battery Saver & Cool",
    subtitle = "60 Hz cap, low-power mode, standby restriction & thermal relief",
    targetHz = 60,
    targetFps = 60,
  ),
  BALANCED(
    id = "balanced",
    title = "Stock Balanced",
    subtitle = "Restore default Android refresh rate, animations & power policies",
    targetHz = 0,
    targetFps = 0,
  ),
}

enum class AngleBackend(val propValue: String, val label: String) {
  VULKAN("vulkan", "Vulkan (Recommended)"),
  OPENGLES("gles", "OpenGL ES"),
  SWIFTSHADER("swiftshader", "SwiftShader CPU"),
}

enum class GpuRenderer(
  val propValue: String,
  val label: String,
  val subtitle: String,
) {
  VULKAN("vulkan", "Vulkan", "Hardware Vulkan + RenderEngine skiavkthreaded"),
  OPENGL_ES("opengl", "OpenGL ES", "Native OpenGL ES pipeline (broadest game compat)"),
  SKIA_GL("skiagl", "SkiaGL", "Skia OpenGL (Best for low-end / older GPUs)"),
  SKIA_VK("skiavk", "SkiaVK", "Skia Vulkan (Lower CPU overhead on Android 11–16)"),
}

data class AngleConfig(
  val enabledGlobally: Boolean = false,
  val backend: AngleBackend = AngleBackend.VULKAN,
  val showInUseDialog: Boolean = false,
  val anglePackages: List<String> = emptyList(),
  val nativePackages: List<String> = emptyList(),
)

data class InstalledAppInfo(
  val packageName: String,
  val appName: String,
)

data class AppFpsRule(
  val packageName: String,
  val targetFps: Int,
  val lowEndOptimize: Boolean = true,
)

data class GpuConfig(
  val renderer: GpuRenderer = GpuRenderer.SKIA_VK,
  val forceGpuComposition: Boolean = false,
  val disableBackpressure: Boolean = false,
  val latchUnsignaled: Boolean = false,
  val forceMsaa4x: Boolean = false,
  val disableHwOverlays: Boolean = false,
  val disableBlurOverhead: Boolean = false,
)

enum class ThermalCategory(val label: String) {
  CPU("CPU"),
  GPU("GPU"),
  BATTERY("Battery"),
  SKIN("Skin"),
  PMIC("PMIC"),
  OTHER("SoC"),
}

data class ThermalZoneReading(
  val id: String,
  val name: String,
  val category: ThermalCategory,
  val tempCelsius: Float,
  val source: String,
)

data class ThermalSnapshot(
  val batteryTempC: Float = 0f,
  val batteryVoltageMv: Int = 0,
  val batteryCurrentMa: Int = 0,
  val batteryLevel: Int = 0,
  val isCharging: Boolean = false,
  val batteryHealthLabel: String = "Good",
  val androidThermalStatus: Int = 0,
  val androidThermalLabel: String = "Nominal",
  val thermalHeadroomPercent: Float? = null,
  val zones: List<ThermalZoneReading> = emptyList(),
  val maxTempC: Float = 0f,
  val sysfsAccessRestricted: Boolean = false,
)

data class MemorySnapshot(
  val totalRamMb: Long = 0L,
  val availableRamMb: Long = 0L,
  val usedRamMb: Long = 0L,
  val usedPercent: Int = 0,
  val lowMemoryThresholdMb: Long = 0L,
  val isLowRamDevice: Boolean = false,
  val zramTotalMb: Long = 0L,
  val zramFreeMb: Long = 0L,
)

enum class ChipsetVendor(val label: String, val familyHint: String) {
  MEDIATEK("MediaTek", "Dimensity / Helio (GED, FPSGO, PowerHAL)"),
  SNAPDRAGON("Snapdragon", "Qualcomm Adreno / QTI Perf"),
  EXYNOS("Exynos", "Samsung Mali / Xclipse"),
  TENSOR("Google Tensor", "ADPF / Mali / Uclamp"),
  UNISOC("Unisoc / Spreadtrum", "Budget Mali / Low-End Smooth"),
}

data class CpuSnapshot(
  val coreCount: Int = 0,
  val coreFrequenciesMhz: List<Int> = emptyList(),
  val maxFrequenciesMhz: List<Int> = emptyList(),
  val cpuLoadPercent: Int = 0,
  val abi: String = "",
  val socModel: String = "",
  val gpuModel: String = "",
  val chipsetVendor: ChipsetVendor = ChipsetVendor.MEDIATEK,
  val currentGovernor: String = "schedutil",
)

data class StorageSnapshot(
  val totalGb: Float = 0f,
  val availableGb: Float = 0f,
  val usedGb: Float = 0f,
  val usedPercent: Int = 0,
)

data class DisplaySnapshot(
  val currentHz: Int = 60,
  val supportedHz: List<Int> = listOf(60),
  val measuredFps: Int = 60,
  val frameTimeMs: Float = 16.6f,
  val jankFrames: Int = 0,
)

data class TelemetryHistoryPoint(
  val timestampMs: Long,
  val maxTempC: Float,
  val cpuLoadPercent: Int,
  val ramUsedPercent: Int,
  val measuredFps: Int,
)

data class DeviceCompatibilityInfo(
  val androidVersionName: String,
  val apiLevel: Int,
  val manufacturer: String,
  val model: String,
  val isAndroid11To16: Boolean,
  val supportsGameManager: Boolean,
  val supportsThermalHeadroom: Boolean,
  val supportsGameDefaultFpsOverride: Boolean,
)

data class TweakItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val category: TweakCategory,
  val tier: TweakTier,
  val minApi: Int,
  val apiSupportLabel: String,
  val applyCommands: List<String>,
  val revertCommands: List<String>,
  val safetyNote: String? = null,
  val isHighRisk: Boolean = false,
)

data class CommandExecutionResult(
  val command: String,
  val exitCode: Int,
  val stdout: String,
  val stderr: String,
  val modeUsed: PrivilegeMode,
  val executedOnDevice: Boolean,
)

data class ExecutionLogEntry(
  val id: Long,
  val timestampLabel: String,
  val mode: PrivilegeMode,
  val command: String,
  val exitCode: Int,
  val output: String,
)

enum class OverlayStyle(val label: String) {
  COMPACT_PILL("FPS Pill"),
  GAMING_HUD("Gaming HUD"),
  FULL_TELEMETRY("Full Telemetry"),
}

enum class OverlayColorPreset(val label: String, val argbHex: Int) {
  CYAN("Cyan", 0xFF00E5FF.toInt()),
  EMERALD("Mint", 0xFF00E676.toInt()),
  AMBER("Amber", 0xFFFFB300.toInt()),
  CORAL("Coral", 0xFFFF4D6D.toInt()),
  WHITE("White", 0xFFF8FAFC.toInt()),
}

enum class OverlayFontSize(val label: String, val spValue: Float) {
  SMALL("Small", 12f),
  MEDIUM("Medium", 15f),
  LARGE("Large", 18f),
}

data class FpsOverlayConfig(
  val style: OverlayStyle = OverlayStyle.GAMING_HUD,
  val colorPreset: OverlayColorPreset = OverlayColorPreset.CYAN,
  val fontSize: OverlayFontSize = OverlayFontSize.MEDIUM,
  val opacity: Float = 0.82f,
  val lockPosition: Boolean = false,
  val showRefreshRate: Boolean = true,
  val showTemp: Boolean = true,
  val showRam: Boolean = false,
  val posX: Int = 36,
  val posY: Int = 140,
)

