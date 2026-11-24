package com.example

import com.example.data.engine.TweakCatalog
import com.example.data.model.AngleBackend
import com.example.data.model.AngleConfig
import com.example.data.model.AppFpsRule
import com.example.data.model.GpuConfig
import com.example.data.model.GpuRenderer
import com.example.data.model.PerformanceProfile
import com.example.data.model.TweakTier
import org.junit.Assert.assertTrue
import org.junit.Test

class TweakCatalogTest {

  @Test
  fun refreshRateCommandsSupport60_90_120AndReset() {
    val cmd90 = TweakCatalog.buildRefreshRateCommands(90)
    assertTrue(cmd90.any { it.contains("peak_refresh_rate 90.0") })
    assertTrue(cmd90.any { it.contains("min_refresh_rate 90.0") })

    val cmdReset = TweakCatalog.buildRefreshRateCommands(0)
    assertTrue(cmdReset.any { it.contains("settings delete system peak_refresh_rate") })
  }

  @Test
  fun fpsLockCommandsAdaptAcrossAndroid11To16() {
    val android11 = TweakCatalog.buildFpsLockCommands(90, "com.tencent.ig", lowEndOptimize = true, apiLevel = 30)
    assertTrue(android11.any { it.contains("am set-standby-bucket com.tencent.ig active") })
    assertTrue(android11.any { it.contains("persist.sys.sf.disable_blurs 1") })

    val android14 = TweakCatalog.buildFpsLockCommands(120, "com.tencent.ig", lowEndOptimize = true, apiLevel = 34)
    assertTrue(android14.any { it.contains("cmd game set --fps 120 com.tencent.ig") })

    val android16 = TweakCatalog.buildFpsLockCommands(60, "com.tencent.ig", lowEndOptimize = true, apiLevel = 36)
    assertTrue(android16.any { it.contains("debug.graphics.game_default_frame_rate.override 60") })
  }

  @Test
  fun perAppBatchBuildsAllRules() {
    val rules =
      listOf(
        AppFpsRule("com.tencent.ig", 90, true),
        AppFpsRule("com.mobile.legends", 60, true),
      )
    val cmds = TweakCatalog.buildPerAppFpsBatchCommands(rules, apiLevel = 35)
    assertTrue(cmds.any { it.contains("com.tencent.ig") })
    assertTrue(cmds.any { it.contains("com.mobile.legends") })
  }

  @Test
  fun angleAndGpuAndProfilesGenerateValidCommands() {
    val angle =
      TweakCatalog.buildAngleCommands(
        AngleConfig(
          enabledGlobally = true,
          backend = AngleBackend.VULKAN,
          showInUseDialog = true,
          anglePackages = listOf("com.tencent.ig"),
        ),
        apiLevel = 35,
      )
    assertTrue(angle.any { it.contains("angle_gl_driver_all_angle 1") })
    assertTrue(angle.any { it.contains("persist.graphics.egl angle") })

    val gpuSkiaVk =
      TweakCatalog.buildGpuCommands(
        GpuConfig(
          renderer = GpuRenderer.SKIA_VK,
          forceGpuComposition = true,
          disableBackpressure = true,
        ),
        apiLevel = 35,
      )
    assertTrue(gpuSkiaVk.any { it.contains("debug.hwui.renderer skiavk") })
    assertTrue(gpuSkiaVk.any { it.contains("debug.renderengine.backend skiavkthreaded") })
    assertTrue(gpuSkiaVk.any { it.contains("debug.sf.disable_backpressure 1") })

    val gpuVulkan = TweakCatalog.buildGpuCommands(GpuConfig(renderer = GpuRenderer.VULKAN), apiLevel = 35)
    assertTrue(gpuVulkan.any { it.contains("ro.hwui.use_vulkan true") })

    val gpuOpenGlEs = TweakCatalog.buildGpuCommands(GpuConfig(renderer = GpuRenderer.OPENGL_ES), apiLevel = 35)
    assertTrue(gpuOpenGlEs.any { it.contains("debug.hwui.renderer opengl") })
    assertTrue(gpuOpenGlEs.any { it.contains("debug.renderengine.backend gles") })

    val gpuSkiaGl = TweakCatalog.buildGpuCommands(GpuConfig(renderer = GpuRenderer.SKIA_GL), apiLevel = 30)
    assertTrue(gpuSkiaGl.any { it.contains("debug.hwui.renderer skiagl") })

    val lowEndProfile = TweakCatalog.buildProfileCommands(PerformanceProfile.LOW_END_SMOOTH, "", apiLevel = 33)
    assertTrue(lowEndProfile.any { it.contains("cached_apps_freezer enabled") })

    val smoothTouch = TweakCatalog.buildSmoothTouchCommands()
    assertTrue(smoothTouch.any { it.contains("long_press_timeout") })
    assertTrue(smoothTouch.any { it.contains("windowsmgr.max_events_per_sec") })

    val mtkShizuku = TweakCatalog.buildChipsetBoostCommands(com.example.data.model.ChipsetVendor.MEDIATEK, forRoot = false, apiLevel = 34)
    assertTrue(mtkShizuku.any { it.contains("mtk.powerhal") })

    val mtkRoot = TweakCatalog.buildChipsetBoostCommands(com.example.data.model.ChipsetVendor.MEDIATEK, forRoot = true, apiLevel = 34)
    assertTrue(mtkRoot.any { it.contains("gx_game_mode") })

    val safeRootFps = TweakCatalog.buildRootSafeGamingFpsCommands()
    assertTrue(safeRootFps.any { it.contains("vm.swappiness=20") })
    assertTrue(safeRootFps.none { it.contains("build.prop") || it.contains("persist.") })

    val tweaks = TweakCatalog.getAllTweaks()
    assertTrue(tweaks.any { it.tier == TweakTier.SHIZUKU })
    assertTrue(tweaks.any { it.id == "shizuku_smooth_touch" })
    assertTrue(tweaks.any { it.id == "root_safe_stable_fps_gaming" })
    assertTrue(tweaks.any { it.tier == TweakTier.ROOT })
  }

  @Test
  fun overlayConfigDefaultsAndPresetsAreValid() {
    val cfg = com.example.data.model.FpsOverlayConfig()
    assertTrue(cfg.opacity in 0.2f..0.98f)
    assertTrue(com.example.data.model.OverlayStyle.entries.size == 3)
    assertTrue(com.example.data.model.OverlayColorPreset.entries.size == 5)
    assertTrue(com.example.data.model.OverlayFontSize.entries.size == 3)
  }

  @Test
  fun superSmoothCommandsIncludeKeyOptimizations() {
    val cmds = TweakCatalog.buildSuperSmoothCommands(bestHz = 120, apiLevel = 35)
    assertTrue(cmds.any { it.contains("peak_refresh_rate 120.0") })
    assertTrue(cmds.any { it.contains("window_animation_scale 0.5") })
    assertTrue(cmds.any { it.contains("cached_apps_freezer enabled") })
    assertTrue(cmds.any { it.contains("disable_window_blurs 1") })
    assertTrue(cmds.any { it.contains("latch_unsignaled 1") })
  }
}
