package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AngleBackend
import com.example.data.model.AngleConfig
import com.example.data.model.AppFpsRule
import com.example.data.model.FpsOverlayConfig
import com.example.data.model.GpuConfig
import com.example.data.model.GpuRenderer
import com.example.data.model.OverlayColorPreset
import com.example.data.model.OverlayFontSize
import com.example.data.model.OverlayStyle
import com.example.data.model.PerformanceProfile
import com.example.data.model.PrivilegeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pulseDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "pulsetune_settings")

data class SavedSettings(
  val privilegeMode: PrivilegeMode = PrivilegeMode.STANDARD,
  val darkTheme: Boolean = true,
  val targetHz: Int = 0,
  val targetFps: Int = 0,
  val targetPackage: String = "com.tencent.ig",
  val lowEndFpsOptimize: Boolean = true,
  val appFpsRules: List<AppFpsRule> = emptyList(),
  val activeProfile: PerformanceProfile = PerformanceProfile.BALANCED,
  val angleConfig: AngleConfig = AngleConfig(),
  val gpuConfig: GpuConfig = GpuConfig(),
  val enabledTweakIds: Set<String> = emptySet(),
  val rootGovernor: String = "schedutil",
  val rootSwappiness: Int = 60,
  val overlayConfig: FpsOverlayConfig = FpsOverlayConfig(),
)

class SettingsRepository(private val context: Context) {

  private object Keys {
    val PRIVILEGE_MODE = stringPreferencesKey("privilege_mode")
    val DARK_THEME = booleanPreferencesKey("dark_theme")
    val TARGET_HZ = intPreferencesKey("target_hz")
    val TARGET_FPS = intPreferencesKey("target_fps")
    val TARGET_PKG = stringPreferencesKey("target_pkg")
    val LOW_END_FPS_OPT = booleanPreferencesKey("low_end_fps_opt")
    val APP_FPS_RULES = stringPreferencesKey("app_fps_rules")
    val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
    val ANGLE_GLOBAL = booleanPreferencesKey("angle_global")
    val ANGLE_BACKEND = stringPreferencesKey("angle_backend")
    val ANGLE_DIALOG = booleanPreferencesKey("angle_dialog")
    val ANGLE_PKGS = stringPreferencesKey("angle_pkgs")
    val NATIVE_PKGS = stringPreferencesKey("native_pkgs")
    val GPU_RENDERER = stringPreferencesKey("gpu_renderer")
    val GPU_COMPOSITION = booleanPreferencesKey("gpu_composition")
    val GPU_NO_BACKPRESSURE = booleanPreferencesKey("gpu_no_backpressure")
    val GPU_LATCH_UNSIGNALED = booleanPreferencesKey("gpu_latch_unsignaled")
    val GPU_MSAA = booleanPreferencesKey("gpu_msaa")
    val GPU_NO_HW_OVERLAYS = booleanPreferencesKey("gpu_no_hw_overlays")
    val GPU_NO_BLUR = booleanPreferencesKey("gpu_no_blur")
    val ENABLED_TWEAKS = stringSetPreferencesKey("enabled_tweaks")
    val ROOT_GOVERNOR = stringPreferencesKey("root_governor")
    val ROOT_SWAPPINESS = intPreferencesKey("root_swappiness")
    val OVERLAY_STYLE = stringPreferencesKey("overlay_style")
    val OVERLAY_COLOR = stringPreferencesKey("overlay_color")
    val OVERLAY_FONT_SIZE = stringPreferencesKey("overlay_font_size")
    val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
    val OVERLAY_LOCK_POS = booleanPreferencesKey("overlay_lock_pos")
    val OVERLAY_SHOW_HZ = booleanPreferencesKey("overlay_show_hz")
    val OVERLAY_SHOW_TEMP = booleanPreferencesKey("overlay_show_temp")
    val OVERLAY_SHOW_RAM = booleanPreferencesKey("overlay_show_ram")
    val OVERLAY_POS_X = intPreferencesKey("overlay_pos_x")
    val OVERLAY_POS_Y = intPreferencesKey("overlay_pos_y")
  }

  val settingsFlow: Flow<SavedSettings> =
    context.pulseDataStore.data.map { prefs ->
      val modeName = prefs[Keys.PRIVILEGE_MODE]
      val mode =
        PrivilegeMode.entries.firstOrNull { it.name == modeName } ?: PrivilegeMode.STANDARD
      val profileId = prefs[Keys.ACTIVE_PROFILE]
      val profile =
        PerformanceProfile.entries.firstOrNull { it.id == profileId }
          ?: PerformanceProfile.BALANCED
      val backendName = prefs[Keys.ANGLE_BACKEND]
      val backend =
        AngleBackend.entries.firstOrNull { it.name == backendName } ?: AngleBackend.VULKAN
      val rendererName = prefs[Keys.GPU_RENDERER]
      val renderer =
        GpuRenderer.entries.firstOrNull { it.name == rendererName } ?: GpuRenderer.SKIA_VK

      val anglePkgs =
        prefs[Keys.ANGLE_PKGS]
          ?.split(",")
          ?.map { it.trim() }
          ?.filter { it.isNotEmpty() }
          .orEmpty()
      val nativePkgs =
        prefs[Keys.NATIVE_PKGS]
          ?.split(",")
          ?.map { it.trim() }
          ?.filter { it.isNotEmpty() }
          .orEmpty()
      val fpsRules =
        prefs[Keys.APP_FPS_RULES]
          ?.split(";")
          ?.mapNotNull { entry ->
            val parts = entry.split("|")
            val pkg = parts.getOrNull(0)?.trim().orEmpty()
            val fps = parts.getOrNull(1)?.toIntOrNull()
            val lowEnd = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: true
            if (pkg.isNotEmpty() && fps != null) AppFpsRule(pkg, fps, lowEnd) else null
          }
          .orEmpty()

      SavedSettings(
        privilegeMode = mode,
        darkTheme = prefs[Keys.DARK_THEME] ?: true,
        targetHz = prefs[Keys.TARGET_HZ] ?: 0,
        targetFps = prefs[Keys.TARGET_FPS] ?: 0,
        targetPackage = prefs[Keys.TARGET_PKG] ?: "com.tencent.ig",
        lowEndFpsOptimize = prefs[Keys.LOW_END_FPS_OPT] ?: true,
        appFpsRules = fpsRules,
        activeProfile = profile,
        angleConfig =
          AngleConfig(
            enabledGlobally = prefs[Keys.ANGLE_GLOBAL] ?: false,
            backend = backend,
            showInUseDialog = prefs[Keys.ANGLE_DIALOG] ?: false,
            anglePackages = anglePkgs,
            nativePackages = nativePkgs,
          ),
        gpuConfig =
          GpuConfig(
            renderer = renderer,
            forceGpuComposition = prefs[Keys.GPU_COMPOSITION] ?: false,
            disableBackpressure = prefs[Keys.GPU_NO_BACKPRESSURE] ?: false,
            latchUnsignaled = prefs[Keys.GPU_LATCH_UNSIGNALED] ?: false,
            forceMsaa4x = prefs[Keys.GPU_MSAA] ?: false,
            disableHwOverlays = prefs[Keys.GPU_NO_HW_OVERLAYS] ?: false,
            disableBlurOverhead = prefs[Keys.GPU_NO_BLUR] ?: false,
          ),
        enabledTweakIds = prefs[Keys.ENABLED_TWEAKS] ?: emptySet(),
        rootGovernor = prefs[Keys.ROOT_GOVERNOR] ?: "schedutil",
        rootSwappiness = prefs[Keys.ROOT_SWAPPINESS] ?: 60,
        overlayConfig =
          FpsOverlayConfig(
            style =
              OverlayStyle.entries.firstOrNull { it.name == prefs[Keys.OVERLAY_STYLE] }
                ?: OverlayStyle.GAMING_HUD,
            colorPreset =
              OverlayColorPreset.entries.firstOrNull { it.name == prefs[Keys.OVERLAY_COLOR] }
                ?: OverlayColorPreset.CYAN,
            fontSize =
              OverlayFontSize.entries.firstOrNull { it.name == prefs[Keys.OVERLAY_FONT_SIZE] }
                ?: OverlayFontSize.MEDIUM,
            opacity = (prefs[Keys.OVERLAY_OPACITY] ?: 0.82f).coerceIn(0.2f, 0.98f),
            lockPosition = prefs[Keys.OVERLAY_LOCK_POS] ?: false,
            showRefreshRate = prefs[Keys.OVERLAY_SHOW_HZ] ?: true,
            showTemp = prefs[Keys.OVERLAY_SHOW_TEMP] ?: true,
            showRam = prefs[Keys.OVERLAY_SHOW_RAM] ?: false,
            posX = prefs[Keys.OVERLAY_POS_X] ?: 36,
            posY = prefs[Keys.OVERLAY_POS_Y] ?: 140,
          ),
      )
    }

  suspend fun setPrivilegeMode(mode: PrivilegeMode) {
    context.pulseDataStore.edit { it[Keys.PRIVILEGE_MODE] = mode.name }
  }

  suspend fun setDarkTheme(dark: Boolean) {
    context.pulseDataStore.edit { it[Keys.DARK_THEME] = dark }
  }

  suspend fun setTargetHz(hz: Int) {
    context.pulseDataStore.edit { it[Keys.TARGET_HZ] = hz }
  }

  suspend fun setTargetFps(fps: Int) {
    context.pulseDataStore.edit { it[Keys.TARGET_FPS] = fps }
  }

  suspend fun setTargetPackage(pkg: String) {
    context.pulseDataStore.edit { it[Keys.TARGET_PKG] = pkg }
  }

  suspend fun setLowEndFpsOptimize(enabled: Boolean) {
    context.pulseDataStore.edit { it[Keys.LOW_END_FPS_OPT] = enabled }
  }

  suspend fun saveAppFpsRules(rules: List<AppFpsRule>) {
    val serialized =
      rules.joinToString(";") { "${it.packageName}|${it.targetFps}|${it.lowEndOptimize}" }
    context.pulseDataStore.edit { it[Keys.APP_FPS_RULES] = serialized }
  }

  suspend fun setActiveProfile(profile: PerformanceProfile) {
    context.pulseDataStore.edit { it[Keys.ACTIVE_PROFILE] = profile.id }
  }

  suspend fun saveAngleConfig(config: AngleConfig) {
    context.pulseDataStore.edit { prefs ->
      prefs[Keys.ANGLE_GLOBAL] = config.enabledGlobally
      prefs[Keys.ANGLE_BACKEND] = config.backend.name
      prefs[Keys.ANGLE_DIALOG] = config.showInUseDialog
      prefs[Keys.ANGLE_PKGS] = config.anglePackages.joinToString(",")
      prefs[Keys.NATIVE_PKGS] = config.nativePackages.joinToString(",")
    }
  }

  suspend fun saveGpuConfig(config: GpuConfig) {
    context.pulseDataStore.edit { prefs ->
      prefs[Keys.GPU_RENDERER] = config.renderer.name
      prefs[Keys.GPU_COMPOSITION] = config.forceGpuComposition
      prefs[Keys.GPU_NO_BACKPRESSURE] = config.disableBackpressure
      prefs[Keys.GPU_LATCH_UNSIGNALED] = config.latchUnsignaled
      prefs[Keys.GPU_MSAA] = config.forceMsaa4x
      prefs[Keys.GPU_NO_HW_OVERLAYS] = config.disableHwOverlays
      prefs[Keys.GPU_NO_BLUR] = config.disableBlurOverhead
    }
  }

  suspend fun setTweakEnabled(tweakId: String, enabled: Boolean) {
    context.pulseDataStore.edit { prefs ->
      val current = prefs[Keys.ENABLED_TWEAKS]?.toMutableSet() ?: mutableSetOf()
      if (enabled) current.add(tweakId) else current.remove(tweakId)
      prefs[Keys.ENABLED_TWEAKS] = current
    }
  }

  suspend fun setRootTuning(governor: String, swappiness: Int) {
    context.pulseDataStore.edit { prefs ->
      prefs[Keys.ROOT_GOVERNOR] = governor
      prefs[Keys.ROOT_SWAPPINESS] = swappiness
    }
  }

  suspend fun saveOverlayConfig(config: FpsOverlayConfig) {
    context.pulseDataStore.edit { prefs ->
      prefs[Keys.OVERLAY_STYLE] = config.style.name
      prefs[Keys.OVERLAY_COLOR] = config.colorPreset.name
      prefs[Keys.OVERLAY_FONT_SIZE] = config.fontSize.name
      prefs[Keys.OVERLAY_OPACITY] = config.opacity
      prefs[Keys.OVERLAY_LOCK_POS] = config.lockPosition
      prefs[Keys.OVERLAY_SHOW_HZ] = config.showRefreshRate
      prefs[Keys.OVERLAY_SHOW_TEMP] = config.showTemp
      prefs[Keys.OVERLAY_SHOW_RAM] = config.showRam
    }
  }

  suspend fun saveOverlayPosition(x: Int, y: Int) {
    context.pulseDataStore.edit { prefs ->
      prefs[Keys.OVERLAY_POS_X] = x
      prefs[Keys.OVERLAY_POS_Y] = y
    }
  }
}
