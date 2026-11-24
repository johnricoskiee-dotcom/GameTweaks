package com.example.data.engine

import android.os.Build
import com.example.data.model.AngleBackend
import com.example.data.model.AngleConfig
import com.example.data.model.ChipsetVendor
import com.example.data.model.GpuConfig
import com.example.data.model.GpuRenderer
import com.example.data.model.PerformanceProfile
import com.example.data.model.TweakCategory
import com.example.data.model.TweakItem
import com.example.data.model.TweakTier

object TweakCatalog {

  fun buildRefreshRateCommands(hz: Int): List<String> {
    if (hz <= 0) {
      return listOf(
        "settings delete system min_refresh_rate",
        "settings delete system peak_refresh_rate",
        "settings delete system user_refresh_rate",
      )
    }
    val f = hz.toFloat()
    return buildList {
      add("settings put system peak_refresh_rate $f")
      add("settings put system min_refresh_rate $f")
      add("settings put system user_refresh_rate $f")
      // Vendor compatibility hints (harmless no-op on stock AOSP)
      add("settings put secure miui_refresh_rate $hz 2>/dev/null || true")
      add("settings put global oneplus_screen_refresh_rate ${if (hz >= 90) 0 else 1} 2>/dev/null || true")
    }
  }

  fun buildFpsLockCommands(
    fps: Int,
    targetPackage: String,
    lowEndOptimize: Boolean = true,
    apiLevel: Int = Build.VERSION.SDK_INT,
  ): List<String> {
    val cleanPkg = targetPackage.trim()
    if (fps <= 0) {
      return buildList {
        add("setprop debug.hwui.fps_divisor 1")
        if (apiLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
          add("setprop debug.graphics.game_default_frame_rate.override 0")
        }
        if (cleanPkg.isNotEmpty() && apiLevel >= Build.VERSION_CODES.TIRAMISU) {
          add("cmd game reset $cleanPkg")
        } else if (cleanPkg.isNotEmpty() && apiLevel >= Build.VERSION_CODES.S) {
          add("cmd game mode standard $cleanPkg")
        }
      }
    }

    return buildList {
      // Android 12-16 GameManager mode + Android 13-16 per-app FPS lock
      if (cleanPkg.isNotEmpty()) {
        if (apiLevel >= Build.VERSION_CODES.S) {
          add("cmd game mode performance $cleanPkg")
        }
        if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
          add("cmd game set --fps $fps $cleanPkg")
          if (lowEndOptimize) {
            // Downscale render buffer slightly on low-end GPUs for rock-solid frame pacing
            add("cmd game set --mode performance --downscale 0.85 --fps $fps $cleanPkg")
          }
        } else if (apiLevel >= Build.VERSION_CODES.R) {
          // Android 11-12 fallback: Angle/SurfaceFlinger frame pacing + standby priority
          add("am set-standby-bucket $cleanPkg active")
        }
      }
      // Android 15-16 Game Default Frame Rate override
      if (apiLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        add("setprop debug.graphics.game_default_frame_rate.override $fps")
      }
      // Display refresh sync lock (Android 11-16 universal)
      add("settings put system peak_refresh_rate ${fps.toFloat()}")
      add("settings put system min_refresh_rate ${fps.toFloat()}")
      // Low-end frame pacing & vsync hints
      add("setprop debug.hwui.fps_divisor 1")
      add("setprop persist.sys.NV_FPSLIMIT $fps")
      if (lowEndOptimize) {
        add("setprop persist.sys.sf.disable_blurs 1")
        add("setprop debug.sf.latch_unsignaled 1")
      }
    }
  }

  fun buildPerAppFpsBatchCommands(
    rules: List<com.example.data.model.AppFpsRule>,
    apiLevel: Int = Build.VERSION.SDK_INT,
  ): List<String> =
    rules.flatMap { rule ->
      buildFpsLockCommands(
        fps = rule.targetFps,
        targetPackage = rule.packageName,
        lowEndOptimize = rule.lowEndOptimize,
        apiLevel = apiLevel,
      )
    }

  fun buildProfileCommands(
    profile: PerformanceProfile,
    targetPackage: String,
    apiLevel: Int = Build.VERSION.SDK_INT,
  ): List<String> {
    val cleanPkg = targetPackage.trim()
    return when (profile) {
      PerformanceProfile.LOW_END_SMOOTH ->
        buildList {
          add("settings put global window_animation_scale 0.5")
          add("settings put global transition_animation_scale 0.5")
          add("settings put global animator_duration_scale 0.5")
          if (apiLevel >= Build.VERSION_CODES.R) {
            add("settings put global cached_apps_freezer enabled")
          }
          add("setprop persist.sys.sf.disable_blurs 1")
          add("setprop debug.hwui.renderer skiagl")
          add("pm trim-caches 4G")
          add("am kill-all")
        }
      PerformanceProfile.GAMING_MAX ->
        buildList {
          add("settings put global low_power 0")
          if (apiLevel >= Build.VERSION_CODES.R) {
            add("cmd power set-fixed-performance-mode-enabled true")
          }
          add("settings put system peak_refresh_rate 120.0")
          add("settings put system min_refresh_rate 120.0")
          add("setprop debug.hwui.renderer skiavk")
          add("setprop debug.sf.hw 1")
          add("setprop debug.egl.hw 1")
          add("setprop debug.sf.disable_backpressure 1")
          add("am kill-all")
          if (cleanPkg.isNotEmpty() && apiLevel >= Build.VERSION_CODES.S) {
            add("cmd game mode performance $cleanPkg")
          }
        }
      PerformanceProfile.BATTERY_SAVER ->
        buildList {
          if (apiLevel >= Build.VERSION_CODES.R) {
            add("cmd power set-fixed-performance-mode-enabled false")
          }
          add("settings put system peak_refresh_rate 60.0")
          add("settings put system min_refresh_rate 60.0")
          add("settings put global low_power 1")
          add("settings put global cached_apps_freezer enabled")
          if (cleanPkg.isNotEmpty() && apiLevel >= Build.VERSION_CODES.S) {
            add("cmd game mode battery $cleanPkg")
          }
          add("dumpsys deviceidle force-idle")
        }
      PerformanceProfile.BALANCED ->
        buildList {
          if (apiLevel >= Build.VERSION_CODES.R) {
            add("cmd power set-fixed-performance-mode-enabled false")
          }
          add("settings put global low_power 0")
          add("settings delete system min_refresh_rate")
          add("settings delete system peak_refresh_rate")
          add("settings put global window_animation_scale 1.0")
          add("settings put global transition_animation_scale 1.0")
          add("settings put global animator_duration_scale 1.0")
          add("setprop debug.sf.disable_backpressure 0")
          if (cleanPkg.isNotEmpty() && apiLevel >= Build.VERSION_CODES.S) {
            add("cmd game mode standard $cleanPkg")
          }
        }
    }
  }

  fun buildAngleCommands(config: AngleConfig, apiLevel: Int = Build.VERSION.SDK_INT): List<String> {
    return buildList {
      add("settings put global angle_gl_driver_all_angle ${if (config.enabledGlobally) 1 else 0}")
      add("settings put global show_angle_in_use_dialog_box ${if (config.showInUseDialog) 1 else 0}")
      add("setprop debug.angle.backend ${config.backend.propValue}")

      if (apiLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        add("setprop persist.graphics.egl ${if (config.enabledGlobally) "angle" else "system"}")
      }

      val combinedPkgs = config.anglePackages + config.nativePackages
      val combinedValues =
        config.anglePackages.map { "angle" } + config.nativePackages.map { "native" }
      if (combinedPkgs.isNotEmpty()) {
        add("settings put global angle_gl_driver_selection_pkgs ${combinedPkgs.joinToString(",")}")
        add(
          "settings put global angle_gl_driver_selection_values ${combinedValues.joinToString(",")}"
        )
      } else {
        add("settings delete global angle_gl_driver_selection_pkgs")
        add("settings delete global angle_gl_driver_selection_values")
      }
    }
  }

  fun buildGpuCommands(config: GpuConfig, apiLevel: Int = Build.VERSION.SDK_INT): List<String> {
    return buildList {
      when (config.renderer) {
        GpuRenderer.VULKAN -> {
          add("setprop debug.hwui.renderer skiavk")
          add("setprop ro.hwui.use_vulkan true 2>/dev/null || true")
          if (apiLevel >= Build.VERSION_CODES.S) {
            add("setprop debug.renderengine.backend skiavkthreaded")
          }
          add("setprop debug.angle.backend vulkan")
        }
        GpuRenderer.OPENGL_ES -> {
          add("setprop debug.hwui.renderer opengl")
          add("setprop ro.hwui.use_vulkan false 2>/dev/null || true")
          if (apiLevel >= Build.VERSION_CODES.S) {
            add("setprop debug.renderengine.backend gles")
          }
          add("setprop debug.angle.backend gles")
        }
        GpuRenderer.SKIA_GL -> {
          add("setprop debug.hwui.renderer skiagl")
          add("setprop ro.hwui.use_vulkan false 2>/dev/null || true")
          if (apiLevel >= Build.VERSION_CODES.S) {
            add("setprop debug.renderengine.backend skiaglthreaded")
          }
        }
        GpuRenderer.SKIA_VK -> {
          add("setprop debug.hwui.renderer skiavk")
          add("setprop ro.hwui.use_vulkan true 2>/dev/null || true")
          if (apiLevel >= Build.VERSION_CODES.S) {
            add("setprop debug.renderengine.backend skiavkthreaded")
          }
        }
      }
      add("setprop debug.sf.hw ${if (config.forceGpuComposition) 1 else 0} 2>/dev/null || true")
      add("setprop debug.egl.hw ${if (config.forceGpuComposition) 1 else 0} 2>/dev/null || true")
      add(
        "setprop debug.composition.type ${if (config.forceGpuComposition) "gpu" else "c2d"} 2>/dev/null || true"
      )
      add("setprop debug.sf.disable_backpressure ${if (config.disableBackpressure) 1 else 0}")
      add("setprop debug.sf.latch_unsignaled ${if (config.latchUnsignaled) 1 else 0}")
      add("setprop debug.egl.force_msaa ${if (config.forceMsaa4x) 1 else 0}")
      add("setprop persist.sys.sf.disable_blurs ${if (config.disableBlurOverhead) 1 else 0} 2>/dev/null || true")
      if (config.disableHwOverlays) {
        add("service call SurfaceFlinger 1008 i32 1 2>/dev/null || true")
      } else {
        add("service call SurfaceFlinger 1008 i32 0 2>/dev/null || true")
      }
    }
  }

  fun buildSmoothTouchCommands(): List<String> =
    listOf(
      "settings put secure long_press_timeout 250",
      "settings put secure multi_press_timeout 200",
      "settings put system pointer_speed 3",
      "settings put global window_animation_scale 0.5",
      "settings put global transition_animation_scale 0.5",
      "settings put global animator_duration_scale 0.5",
      "setprop touch.pressure.scale 0.001 2>/dev/null || true",
      "setprop windowsmgr.max_events_per_sec 150 2>/dev/null || true",
      "setprop view.scroll_friction 10 2>/dev/null || true",
      "setprop debug.sf.latch_unsignaled 1",
      "setprop debug.sf.disable_backpressure 1",
    )

  fun buildSuperSmoothCommands(
    bestHz: Int,
    apiLevel: Int = Build.VERSION.SDK_INT,
  ): List<String> =
    buildList {
      val targetHz = bestHz.coerceAtLeast(60).toFloat()
      add("settings put system peak_refresh_rate $targetHz")
      add("settings put system min_refresh_rate $targetHz")
      add("settings put global window_animation_scale 0.5")
      add("settings put global transition_animation_scale 0.5")
      add("settings put global animator_duration_scale 0.5")
      add("settings put secure long_press_timeout 250")
      add("settings put system pointer_speed 3")
      if (apiLevel >= Build.VERSION_CODES.R) {
        add("settings put global cached_apps_freezer enabled")
        add("cmd power set-fixed-performance-mode-enabled true")
      }
      if (apiLevel >= Build.VERSION_CODES.S) {
        add("settings put global disable_window_blurs 1")
      }
      add("setprop debug.sf.latch_unsignaled 1")
      add("setprop debug.sf.disable_backpressure 1")
      add("setprop debug.hwui.fps_divisor 1")
      add("am kill-all")
      add("pm trim-caches 4G")
    }

  fun buildChipsetBoostCommands(
    vendor: ChipsetVendor,
    forRoot: Boolean,
    apiLevel: Int = Build.VERSION.SDK_INT,
  ): List<String> =
    when (vendor) {
      ChipsetVendor.MEDIATEK ->
        if (!forRoot) {
          buildList {
            add("setprop persist.sys.mtk.powerhal.hint 1")
            add("setprop persist.vendor.mtk.perf.boost 1")
            add("setprop vendor.powerhal.init 1")
            add("setprop debug.mtk.aee.disable 1")
            add("setprop debug.hwui.renderer skiavk")
            if (apiLevel >= Build.VERSION_CODES.R) {
              add("cmd power set-fixed-performance-mode-enabled true")
            }
          }
        } else {
          listOf(
            "echo 1 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null || true",
            "echo 1 > /sys/module/ged/parameters/gx_force_cpu_boost 2>/dev/null || true",
            "echo 1 > /sys/kernel/fpsgo/common/fpsgo_enable 2>/dev/null || true",
            "echo 1 > /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null || true",
            "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$cpu 2>/dev/null || true; done",
          )
        }
      ChipsetVendor.SNAPDRAGON ->
        if (!forRoot) {
          buildList {
            add("setprop persist.vendor.qti.perf.boost 1")
            add("setprop vendor.perf.gestureflingboost.enable true")
            add("setprop debug.qti.perf.enable 1")
            add("setprop debug.hwui.renderer skiavk")
            if (apiLevel >= Build.VERSION_CODES.R) {
              add("cmd power set-fixed-performance-mode-enabled true")
            }
          }
        } else {
          listOf(
            "echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null || true",
            "echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null || true",
            "echo 1 > /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null || true",
            "echo 0 > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null || true",
            "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$cpu 2>/dev/null || true; done",
          )
        }
      ChipsetVendor.EXYNOS ->
        if (!forRoot) {
          buildList {
            add("setprop persist.sys.exynos.perf 1")
            add("setprop debug.sf.latch_unsignaled 1")
            add("setprop debug.hwui.renderer skiavk")
            if (apiLevel >= Build.VERSION_CODES.R) {
              add("cmd power set-fixed-performance-mode-enabled true")
            }
          }
        } else {
          listOf(
            "echo always_on > /sys/class/misc/mali0/device/power_policy 2>/dev/null || true",
            "echo performance > /sys/kernel/gpu/gpu_governor 2>/dev/null || true",
            "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$cpu 2>/dev/null || true; done",
          )
        }
      ChipsetVendor.TENSOR ->
        if (!forRoot) {
          buildList {
            add("setprop persist.vendor.powerhal.adpf 1")
            add("setprop debug.hwui.renderer skiavk")
            add("setprop debug.sf.disable_backpressure 1")
            if (apiLevel >= Build.VERSION_CODES.R) {
              add("cmd power set-fixed-performance-mode-enabled true")
            }
          }
        } else {
          listOf(
            "echo 40 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || true",
            "echo always_on > /sys/class/misc/mali0/device/power_policy 2>/dev/null || true",
            "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo schedutil > \$cpu 2>/dev/null || true; done",
          )
        }
      ChipsetVendor.UNISOC ->
        if (!forRoot) {
          buildList {
            add("setprop persist.sys.sprd.perf 1")
            add("setprop persist.sys.sf.disable_blurs 1")
            add("setprop debug.hwui.renderer skiagl")
            add("settings put global cached_apps_freezer enabled")
            add("am kill-all")
          }
        } else {
          listOf(
            "sysctl -w vm.swappiness=25",
            "for g in /sys/class/devfreq/*/governor; do echo performance > \$g 2>/dev/null || true; done",
            "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$cpu 2>/dev/null || true; done",
          )
        }
    }

  fun buildRootSafeGamingFpsCommands(): List<String> =
    listOf(
      // Volatile-only tuning: NO build.prop, NO /system edits, NO persist.* writes -> 0% bootloop risk
      "for g in /sys/devices/system/cpu/cpu*/cpufreq/schedutil/up_rate_limit_us; do [ -w \"\$g\" ] && echo 500 > \"\$g\" 2>/dev/null || true; done",
      "for g in /sys/devices/system/cpu/cpu*/cpufreq/schedutil/down_rate_limit_us; do [ -w \"\$g\" ] && echo 5000 > \"\$g\" 2>/dev/null || true; done",
      "for c in /dev/cpuctl/top-app/cpu.uclamp.min /dev/stune/top-app/schedtune.boost; do [ -w \"\$c\" ] && echo 35 > \"\$c\" 2>/dev/null || true; done",
      "for q in /sys/block/*/queue/iostats; do [ -w \"\$q\" ] && echo 0 > \"\$q\" 2>/dev/null || true; done",
      "for r in /sys/block/*/queue/read_ahead_kb; do [ -w \"\$r\" ] && echo 512 > \"\$r\" 2>/dev/null || true; done",
      "sysctl -w vm.swappiness=20 vm.vfs_cache_pressure=60 vm.dirty_ratio=12 vm.dirty_background_ratio=4",
      "setprop debug.sf.latch_unsignaled 1",
      "setprop debug.sf.disable_backpressure 1",
    )

  fun buildRootGovernorCommand(governor: String): List<String> =
    listOf(
      "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $governor > \$cpu 2>/dev/null; done"
    )

  fun buildRootSwappinessCommand(swappiness: Int, vfsCachePressure: Int): List<String> =
    listOf(
      "sysctl -w vm.swappiness=$swappiness",
      "sysctl -w vm.vfs_cache_pressure=$vfsCachePressure",
    )

  fun buildRootIoSchedulerCommand(scheduler: String, readAheadKb: Int): List<String> =
    listOf(
      "for q in /sys/block/*/queue/scheduler; do echo $scheduler > \$q 2>/dev/null; done",
      "for r in /sys/block/*/queue/read_ahead_kb; do echo $readAheadKb > \$r 2>/dev/null; done",
    )

  fun getAllTweaks(): List<TweakItem> =
    listOf(
      // --- SHIZUKU / ADB TWEAKS (Android 11-16 & backward compatible) ---
      TweakItem(
        id = "shizuku_anim_05x",
        title = "Snappy 0.5x Animations",
        subtitle = "Cuts window, transition & animator durations in half for low-end smoothness",
        category = TweakCategory.LOW_END,
        tier = TweakTier.SHIZUKU,
        minApi = 24,
        apiSupportLabel = "Android 7–16",
        applyCommands =
          listOf(
            "settings put global window_animation_scale 0.5",
            "settings put global transition_animation_scale 0.5",
            "settings put global animator_duration_scale 0.5",
          ),
        revertCommands =
          listOf(
            "settings put global window_animation_scale 1.0",
            "settings put global transition_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0",
          ),
      ),
      TweakItem(
        id = "shizuku_cached_freezer",
        title = "Cached Apps Freezer",
        subtitle = "Freezes background cached apps to stop wakeups & save RAM/battery",
        category = TweakCategory.LOW_END,
        tier = TweakTier.SHIZUKU,
        minApi = 30,
        apiSupportLabel = "Android 11–16",
        applyCommands = listOf("settings put global cached_apps_freezer enabled"),
        revertCommands = listOf("settings put global cached_apps_freezer device_default"),
      ),
      TweakItem(
        id = "shizuku_fixed_perf",
        title = "Fixed Performance Mode",
        subtitle = "Signals PowerHAL to sustain high CPU/GPU clocks during sustained gaming",
        category = TweakCategory.PROFILES,
        tier = TweakTier.SHIZUKU,
        minApi = 30,
        apiSupportLabel = "Android 11–16",
        applyCommands = listOf("cmd power set-fixed-performance-mode-enabled true"),
        revertCommands = listOf("cmd power set-fixed-performance-mode-enabled false"),
        safetyNote = "Increases heat & battery drain while active.",
      ),
      TweakItem(
        id = "shizuku_ram_kill_all",
        title = "Aggressive Background RAM Trim",
        subtitle = "Safely stops cached background processes and trims app caches up to 4GB",
        category = TweakCategory.LOW_END,
        tier = TweakTier.SHIZUKU,
        minApi = 24,
        apiSupportLabel = "Android 7–16",
        applyCommands = listOf("am kill-all", "pm trim-caches 4G"),
        revertCommands = emptyList(),
      ),
      TweakItem(
        id = "shizuku_art_speed_profile",
        title = "ART Speed-Profile Precompile",
        subtitle = "Compiles hot code paths with JIT profiles to cut frame drops on older SoCs",
        category = TweakCategory.LOW_END,
        tier = TweakTier.SHIZUKU,
        minApi = 24,
        apiSupportLabel = "Android 7–16",
        applyCommands = listOf("cmd package compile -m speed-profile -a"),
        revertCommands = emptyList(),
        safetyNote = "Takes 30–90s in background on first run.",
      ),
      TweakItem(
        id = "shizuku_disable_blurs",
        title = "Disable Window Blur Overhead",
        subtitle = "Eliminates costly Gaussian window blurs on budget GPUs",
        category = TweakCategory.GPU_ANGLE,
        tier = TweakTier.SHIZUKU,
        minApi = 31,
        apiSupportLabel = "Android 12–16",
        applyCommands =
          listOf(
            "settings put global disable_window_blurs 1",
            "setprop persist.sys.sf.disable_blurs 1",
          ),
        revertCommands =
          listOf(
            "settings put global disable_window_blurs 0",
            "setprop persist.sys.sf.disable_blurs 0",
          ),
      ),
      TweakItem(
        id = "shizuku_doze_force",
        title = "Force Deep Doze Idle",
        subtitle = "Immediately engages deep Doze power saving when screen turns off",
        category = TweakCategory.PROFILES,
        tier = TweakTier.SHIZUKU,
        minApi = 24,
        apiSupportLabel = "Android 7–16",
        applyCommands = listOf("dumpsys deviceidle force-idle"),
        revertCommands = listOf("dumpsys deviceidle unforce"),
      ),
      TweakItem(
        id = "shizuku_hz_120",
        title = "Lock Display 120 Hz",
        subtitle = "Forces min and peak refresh rate to 120 Hz across all apps",
        category = TweakCategory.DISPLAY_FPS,
        tier = TweakTier.SHIZUKU,
        minApi = 30,
        apiSupportLabel = "Android 11–16",
        applyCommands = buildRefreshRateCommands(120),
        revertCommands = buildRefreshRateCommands(0),
      ),
      TweakItem(
        id = "shizuku_bg_dexopt",
        title = "ART Background Dexopt & Standby Trim",
        subtitle = "Runs system dexopt job & trims inactive background standby buckets via Shizuku",
        category = TweakCategory.LOW_END,
        tier = TweakTier.SHIZUKU,
        minApi = 28,
        apiSupportLabel = "Android 9–16",
        applyCommands = listOf("cmd package bg-dexopt-job", "am kill-all"),
        revertCommands = emptyList(),
      ),
      TweakItem(
        id = "shizuku_smooth_touch",
        title = "Ultra Smooth Touch & Scroll Latency",
        subtitle = "Boosts touch sampling responsiveness, scroll friction & latches unsignaled frames",
        category = TweakCategory.LOW_END,
        tier = TweakTier.SHIZUKU,
        minApi = 24,
        apiSupportLabel = "Android 7–16",
        applyCommands = buildSmoothTouchCommands(),
        revertCommands =
          listOf(
            "settings put secure long_press_timeout 400",
            "settings put system pointer_speed 0",
            "setprop debug.sf.latch_unsignaled 0",
          ),
      ),
      // --- ROOT-ONLY TWEAKS (Kernel / Sysfs / Sysctl) ---
      TweakItem(
        id = "root_cpu_perf",
        title = "CPU Governor: Performance",
        subtitle = "Pins all CPU cluster governors to max frequency for zero ramp-up stutter",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 24,
        apiSupportLabel = "Root (Android 7–16)",
        applyCommands = buildRootGovernorCommand("performance"),
        revertCommands = buildRootGovernorCommand("schedutil"),
        safetyNote = "High power & thermal load. Monitor CPU/Battery heat closely.",
        isHighRisk = true,
      ),
      TweakItem(
        id = "root_gpu_adreno_mali",
        title = "GPU Devfreq Boost (Adreno / Mali)",
        subtitle = "Locks GPU devfreq governor to performance and disables idle clock gating",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 24,
        apiSupportLabel = "Root (Adreno/Mali)",
        applyCommands =
          listOf(
            "echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null || true",
            "echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null || true",
            "echo always_on > /sys/class/misc/mali0/device/power_policy 2>/dev/null || true",
          ),
        revertCommands =
          listOf(
            "echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null || true",
            "echo 0 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null || true",
            "echo coarse_demand > /sys/class/misc/mali0/device/power_policy 2>/dev/null || true",
          ),
        safetyNote = "Root-only sysfs write. Automatically skips unsupported GPU paths.",
        isHighRisk = true,
      ),
      TweakItem(
        id = "root_drop_caches",
        title = "Kernel PageCache & Slab Flush",
        subtitle = "Flushes dirty filesystem buffers & reclaims kernel pagecache/dentries",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 24,
        apiSupportLabel = "Root (All Kernel)",
        applyCommands = listOf("sync && echo 3 > /proc/sys/vm/drop_caches"),
        revertCommands = emptyList(),
      ),
      TweakItem(
        id = "root_vm_low_end",
        title = "Low-End ZRAM & VM Tuning",
        subtitle = "Sets vm.swappiness=30 & vfs_cache_pressure=75 to prioritize active app RAM",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 24,
        apiSupportLabel = "Root (Android 7–16)",
        applyCommands = buildRootSwappinessCommand(swappiness = 30, vfsCachePressure = 75),
        revertCommands = buildRootSwappinessCommand(swappiness = 60, vfsCachePressure = 100),
      ),
      TweakItem(
        id = "root_io_scheduler",
        title = "Flash Storage I/O Read-Ahead 512KB",
        subtitle = "Tunes block queue scheduler & read-ahead buffer for faster texture streaming",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 24,
        apiSupportLabel = "Root (UFS/eMMC)",
        applyCommands = buildRootIoSchedulerCommand(scheduler = "mq-deadline", readAheadKb = 512),
        revertCommands = buildRootIoSchedulerCommand(scheduler = "bfq", readAheadKb = 128),
      ),
      TweakItem(
        id = "root_kernel_sched_uclamp",
        title = "Kernel Scheduler & Uclamp Latency Boost",
        subtitle = "Boosts top-app uclamp.min & reduces kernel dirty writeback stalls",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 29,
        apiSupportLabel = "Root (Android 10–16)",
        applyCommands =
          listOf(
            "echo 30 > /dev/stune/top-app/schedtune.boost 2>/dev/null || echo 30 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || true",
            "sysctl -w vm.dirty_ratio=15 vm.dirty_background_ratio=5",
          ),
        revertCommands =
          listOf(
            "echo 10 > /dev/stune/top-app/schedtune.boost 2>/dev/null || echo 0 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || true",
            "sysctl -w vm.dirty_ratio=20 vm.dirty_background_ratio=10",
          ),
      ),
      TweakItem(
        id = "root_lmkd_tuning",
        title = "LMKD Multitasking Retention",
        subtitle = "Adjusts PSI stall threshold so Android kills fewer background apps on 4–6GB RAM",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 29,
        apiSupportLabel = "Root (Android 10–16)",
        applyCommands =
          listOf(
            "setprop persist.device_config.lmkd_native.psi_partial_stall_ms 200",
            "setprop persist.device_config.lmkd_native.psi_complete_stall_ms 700",
            "setprop ro.lmk.kill_heaviest_task true",
          ),
        revertCommands =
          listOf(
            "setprop persist.device_config.lmkd_native.psi_partial_stall_ms 70",
            "setprop persist.device_config.lmkd_native.psi_complete_stall_ms 700",
          ),
      ),
      TweakItem(
        id = "shizuku_mediatek_boost",
        title = "MediaTek Dimensity / Helio Boost (Shizuku)",
        subtitle = "Activates MediaTek PowerHAL hints, perf boost & SkiaVK pipeline via Shizuku",
        category = TweakCategory.PROFILES,
        tier = TweakTier.SHIZUKU,
        minApi = 30,
        apiSupportLabel = "Android 11–16 (MTK)",
        applyCommands = buildChipsetBoostCommands(ChipsetVendor.MEDIATEK, forRoot = false),
        revertCommands =
          listOf(
            "setprop persist.sys.mtk.powerhal.hint 0",
            "setprop persist.vendor.mtk.perf.boost 0",
            "cmd power set-fixed-performance-mode-enabled false",
          ),
      ),
      TweakItem(
        id = "root_mediatek_kernel_boost",
        title = "MediaTek GED & FPSGO Kernel Boost (Root)",
        subtitle = "Enables GED game mode, gx_force_cpu_boost, FPSGO & performance CPU governor",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 24,
        apiSupportLabel = "Root (MediaTek SoC)",
        applyCommands = buildChipsetBoostCommands(ChipsetVendor.MEDIATEK, forRoot = true),
        revertCommands =
          listOf(
            "echo 0 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null || true",
            "echo 0 > /sys/module/ged/parameters/gx_force_cpu_boost 2>/dev/null || true",
            "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo schedutil > \$cpu 2>/dev/null || true; done",
          ),
        safetyNote = "Root-only MediaTek GED/FPSGO sysfs boost. Monitor thermals.",
        isHighRisk = true,
      ),
      TweakItem(
        id = "shizuku_snapdragon_boost",
        title = "Qualcomm Snapdragon Perf Boost (Shizuku)",
        subtitle = "Enables QTI perf boost, gesture fling boost & fixed performance mode",
        category = TweakCategory.PROFILES,
        tier = TweakTier.SHIZUKU,
        minApi = 30,
        apiSupportLabel = "Android 11–16 (QTI)",
        applyCommands = buildChipsetBoostCommands(ChipsetVendor.SNAPDRAGON, forRoot = false),
        revertCommands =
          listOf(
            "setprop persist.vendor.qti.perf.boost 0",
            "cmd power set-fixed-performance-mode-enabled false",
          ),
      ),
      TweakItem(
        id = "root_snapdragon_adreno_boost",
        title = "Snapdragon Adreno KGSL Kernel Boost (Root)",
        subtitle = "Locks Adreno force_clk_on, force_bus_on & disables GPU throttling",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 24,
        apiSupportLabel = "Root (Snapdragon)",
        applyCommands = buildChipsetBoostCommands(ChipsetVendor.SNAPDRAGON, forRoot = true),
        revertCommands =
          listOf(
            "echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null || true",
            "echo 0 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null || true",
            "echo 1 > /sys/class/kgsl/kgsl-3d0/throttling 2>/dev/null || true",
          ),
        safetyNote = "Root-only Adreno kgsl-3d0 tuning. Increases GPU heat under load.",
        isHighRisk = true,
      ),
      TweakItem(
        id = "root_safe_stable_fps_gaming",
        title = "Anti-Bootloop Smooth Gameplay & Stable FPS (Root)",
        subtitle =
          "Volatile-only schedutil ramp, top-app uclamp, zero-iostats & VM tuning (resets cleanly on reboot, never touches build.prop/persist)",
        category = TweakCategory.KERNEL_ROOT,
        tier = TweakTier.ROOT,
        minApi = 24,
        apiSupportLabel = "Root Safe (Android 7–16)",
        applyCommands = buildRootSafeGamingFpsCommands(),
        revertCommands =
          listOf(
            "for c in /dev/cpuctl/top-app/cpu.uclamp.min /dev/stune/top-app/schedtune.boost; do [ -w \"\$c\" ] && echo 0 > \"\$c\" 2>/dev/null || true; done",
            "sysctl -w vm.swappiness=60 vm.vfs_cache_pressure=100 vm.dirty_ratio=20 vm.dirty_background_ratio=10",
            "setprop debug.sf.latch_unsignaled 0",
            "setprop debug.sf.disable_backpressure 0",
          ),
        safetyNote = "Zero-bootloop safe: volatile runtime sysfs/sysctl only; reboot restores stock.",
      ),
    )
}
