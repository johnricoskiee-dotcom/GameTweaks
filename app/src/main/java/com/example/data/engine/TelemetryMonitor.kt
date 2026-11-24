package com.example.data.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.view.Choreographer
import android.view.Display
import com.example.data.model.ChipsetVendor
import com.example.data.model.CpuSnapshot
import com.example.data.model.DeviceCompatibilityInfo
import com.example.data.model.DisplaySnapshot
import com.example.data.model.InstalledAppInfo
import com.example.data.model.MemorySnapshot
import com.example.data.model.PrivilegeMode
import com.example.data.model.StorageSnapshot
import com.example.data.model.ThermalCategory
import com.example.data.model.ThermalSnapshot
import com.example.data.model.ThermalZoneReading
import java.io.File
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelemetryMonitor(
  private val context: Context,
  private val privilegeEngine: PrivilegeShellEngine,
) {
  private val activityManager =
    context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
  private val batteryManager =
    context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
  private val displayManager =
    context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

  private var prevCpuTotal = 0L
  private var prevCpuIdle = 0L
  @Volatile private var cachedGpuModel: String? = null
  @Volatile private var cachedSocName: String? = null

  @Volatile private var measuredFps = 60
  @Volatile private var frameTimeMs = 16.6f
  @Volatile private var jankCount = 0
  private var frameCounter = 0
  private var windowStartNs = 0L
  private var lastFrameNs = 0L
  private var isTrackingFrames = false

  private val frameCallback =
    object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
        if (!isTrackingFrames) return
        if (windowStartNs == 0L) {
          windowStartNs = frameTimeNanos
          lastFrameNs = frameTimeNanos
        }
        val deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000f
        if (deltaMs > 0f && deltaMs < 250f) {
          frameTimeMs = deltaMs
          if (deltaMs > 22f) {
            jankCount++
          }
        }
        lastFrameNs = frameTimeNanos
        frameCounter++

        val elapsedNs = frameTimeNanos - windowStartNs
        if (elapsedNs >= 1_000_000_000L) {
          val fps = ((frameCounter * 1_000_000_000.0) / elapsedNs).roundToInt().coerceAtLeast(1)
          measuredFps = fps
          frameCounter = 0
          windowStartNs = frameTimeNanos
        }
        Choreographer.getInstance().postFrameCallback(this)
      }
    }

  fun startFrameMonitor() {
    if (isTrackingFrames) return
    isTrackingFrames = true
    windowStartNs = 0L
    lastFrameNs = 0L
    frameCounter = 0
    Choreographer.getInstance().postFrameCallback(frameCallback)
  }

  fun stopFrameMonitor() {
    isTrackingFrames = false
    Choreographer.getInstance().removeFrameCallback(frameCallback)
  }

  fun getCompatibilityInfo(): DeviceCompatibilityInfo {
    val sdk = Build.VERSION.SDK_INT
    val versionName =
      when {
        sdk >= 36 -> "Android 16"
        sdk == 35 -> "Android 15"
        sdk == 34 -> "Android 14"
        sdk == 33 -> "Android 13"
        sdk == 32 -> "Android 12L"
        sdk == 31 -> "Android 12"
        sdk == 30 -> "Android 11"
        sdk == 29 -> "Android 10"
        sdk == 28 -> "Android 9"
        sdk >= 26 -> "Android 8"
        else -> "Android 7"
      }
    return DeviceCompatibilityInfo(
      androidVersionName = versionName,
      apiLevel = sdk,
      manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
      model = Build.MODEL,
      isAndroid11To16 = sdk in 30..36,
      supportsGameManager = sdk >= Build.VERSION_CODES.S,
      supportsThermalHeadroom = sdk >= Build.VERSION_CODES.R,
      supportsGameDefaultFpsOverride = sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM,
    )
  }

  suspend fun readThermalSnapshot(privilegeMode: PrivilegeMode): ThermalSnapshot =
    withContext(Dispatchers.IO) {
      val batteryIntent =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
      val batteryTempC = tempTenths / 10f
      val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
      val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
      val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
      val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
      val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
      val isCharging =
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
          status == BatteryManager.BATTERY_STATUS_FULL
      val healthInt =
        batteryIntent?.getIntExtra(
          BatteryManager.EXTRA_HEALTH,
          BatteryManager.BATTERY_HEALTH_GOOD,
        ) ?: BatteryManager.BATTERY_HEALTH_GOOD
      val healthLabel =
        when (healthInt) {
          BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
          BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
          BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded"
          BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
          else -> "Good"
        }

      val rawCurrentUa =
        runCatching { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
          .getOrDefault(0)
      val currentMa =
        if (kotlin.math.abs(rawCurrentUa) > 10_000) rawCurrentUa / 1000 else rawCurrentUa

      val androidThermalStatus =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          runCatching { powerManager.currentThermalStatus }.getOrDefault(0)
        } else {
          0
        }
      val androidThermalLabel =
        when (androidThermalStatus) {
          0 -> "Nominal"
          1 -> "Light"
          2 -> "Moderate"
          3 -> "Severe"
          4 -> "Critical"
          5 -> "Emergency"
          6 -> "Shutdown"
          else -> "Nominal"
        }

      val headroomPct =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          val headroom = runCatching { powerManager.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
          if (!headroom.isNaN() && headroom >= 0f) {
            (headroom * 100f).coerceIn(0f, 150f)
          } else {
            null
          }
        } else {
          null
        }

      val (zones, restricted) = readThermalZones(privilegeMode)
      val allZones = buildList {
        if (batteryTempC > 0f) {
          add(
            ThermalZoneReading(
              id = "battery_hal",
              name = "Battery Cell",
              category = ThermalCategory.BATTERY,
              tempCelsius = batteryTempC,
              source = "BatteryManager",
            )
          )
        }
        addAll(zones)
      }
      val maxTemp = allZones.maxOfOrNull { it.tempCelsius } ?: batteryTempC

      ThermalSnapshot(
        batteryTempC = batteryTempC,
        batteryVoltageMv = voltageMv,
        batteryCurrentMa = currentMa,
        batteryLevel = batteryPct,
        isCharging = isCharging,
        batteryHealthLabel = healthLabel,
        androidThermalStatus = androidThermalStatus,
        androidThermalLabel = androidThermalLabel,
        thermalHeadroomPercent = headroomPct,
        zones = allZones.take(12),
        maxTempC = maxTemp,
        sysfsAccessRestricted = restricted,
      )
    }

  private suspend fun readThermalZones(
    privilegeMode: PrivilegeMode
  ): Pair<List<ThermalZoneReading>, Boolean> {
    val zones = mutableListOf<ThermalZoneReading>()
    var sawPermissionIssue = false

    val thermalDir = File("/sys/class/thermal")
    val zoneDirs =
      thermalDir
        .listFiles { file -> file.name.startsWith("thermal_zone") }
        ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 999 }
        .orEmpty()

    for (dir in zoneDirs.take(18)) {
      val typeFile = File(dir, "type")
      val tempFile = File(dir, "temp")
      var typeName = runCatching { typeFile.readText().trim() }.getOrNull()
      var tempRaw = runCatching { tempFile.readText().trim() }.getOrNull()

      if ((typeName == null || tempRaw == null) && privilegeMode != PrivilegeMode.STANDARD) {
        typeName = privilegeEngine.readPrivilegedSysFile(typeFile.absolutePath).ifEmpty { null }
        tempRaw = privilegeEngine.readPrivilegedSysFile(tempFile.absolutePath).ifEmpty { null }
      } else if (typeName == null || tempRaw == null) {
        sawPermissionIssue = true
      }

      val parsedTemp = tempRaw?.toFloatOrNull()?.let { normalizeTemp(it) }
      if (typeName != null && parsedTemp != null && parsedTemp in 5f..125f) {
        zones.add(
          ThermalZoneReading(
            id = dir.name,
            name = formatThermalName(typeName),
            category = classifyThermalZone(typeName),
            tempCelsius = parsedTemp,
            source = dir.name,
          )
        )
      }
    }

    if (zones.isEmpty() && privilegeMode != PrivilegeMode.STANDARD) {
      val batchCmd =
        "for z in /sys/class/thermal/thermal_zone*; do echo \"\$(basename \$z)|\$(cat \$z/type 2>/dev/null)|\$(cat \$z/temp 2>/dev/null)\"; done"
      val res = privilegeEngine.executeCommand(batchCmd, privilegeMode)
      if (res.exitCode == 0 && res.stdout.isNotEmpty()) {
        res.stdout.lines().take(18).forEach { line ->
          val parts = line.split("|")
          if (parts.size == 3) {
            val zId = parts[0].trim()
            val tName = parts[1].trim()
            val tVal = parts[2].trim().toFloatOrNull()?.let { normalizeTemp(it) }
            if (tName.isNotEmpty() && tVal != null && tVal in 5f..125f) {
              zones.add(
                ThermalZoneReading(
                  id = zId,
                  name = formatThermalName(tName),
                  category = classifyThermalZone(tName),
                  tempCelsius = tVal,
                  source = "${privilegeMode.shortLabel} sysfs",
                )
              )
            }
          }
        }
        sawPermissionIssue = false
      }
    }

    return zones to (sawPermissionIssue && zones.isEmpty())
  }

  private fun normalizeTemp(raw: Float): Float =
    when {
      raw > 1000f -> raw / 1000f
      raw > 150f -> raw / 10f
      else -> raw
    }

  private fun classifyThermalZone(type: String): ThermalCategory {
    val lower = type.lowercase()
    return when {
      "cpu" in lower || "tsens" in lower || "core" in lower || "cluster" in lower || "big" in lower || "little" in lower ->
        ThermalCategory.CPU
      "gpu" in lower || "gpuss" in lower || "mali" in lower || "adreno" in lower ->
        ThermalCategory.GPU
      "batt" in lower || "bms" in lower -> ThermalCategory.BATTERY
      "skin" in lower || "xo" in lower || "therm" in lower || "quiet" in lower ->
        ThermalCategory.SKIN
      "pmic" in lower || "chg" in lower || "pa" in lower -> ThermalCategory.PMIC
      else -> ThermalCategory.OTHER
    }
  }

  private fun formatThermalName(raw: String): String =
    raw.replace("-", " ").replace("_", " ").split(" ").filter { it.isNotBlank() }.joinToString(" ") {
      word ->
      if (word.length <= 3) word.uppercase() else word.replaceFirstChar { it.uppercase() }
    }

  suspend fun readMemorySnapshot(): MemorySnapshot =
    withContext(Dispatchers.IO) {
      val memInfo = ActivityManager.MemoryInfo()
      activityManager.getMemoryInfo(memInfo)

      val totalMb = memInfo.totalMem / (1024L * 1024L)
      val availMb = memInfo.availMem / (1024L * 1024L)
      val usedMb = (totalMb - availMb).coerceAtLeast(0L)
      val usedPct = if (totalMb > 0) ((usedMb * 100L) / totalMb).toInt().coerceIn(0, 100) else 0
      val thresholdMb = memInfo.threshold / (1024L * 1024L)

      var zramTotalMb = 0L
      var zramFreeMb = 0L
      runCatching {
        File("/proc/meminfo").forEachLine { line ->
          when {
            line.startsWith("SwapTotal:") -> {
              val kb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
              zramTotalMb = kb / 1024L
            }
            line.startsWith("SwapFree:") -> {
              val kb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
              zramFreeMb = kb / 1024L
            }
          }
        }
      }

      MemorySnapshot(
        totalRamMb = totalMb,
        availableRamMb = availMb,
        usedRamMb = usedMb,
        usedPercent = usedPct,
        lowMemoryThresholdMb = thresholdMb,
        isLowRamDevice = activityManager.isLowRamDevice,
        zramTotalMb = zramTotalMb,
        zramFreeMb = zramFreeMb,
      )
    }

  suspend fun readCpuSnapshot(privilegeMode: PrivilegeMode): CpuSnapshot =
    withContext(Dispatchers.IO) {
      val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
      val curFreqs = mutableListOf<Int>()
      val maxFreqs = mutableListOf<Int>()
      var governor = "schedutil"

      for (i in 0 until cores.coerceAtMost(12)) {
        val curPath = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
        val maxPath = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"
        val curKhz = runCatching { File(curPath).readText().trim().toIntOrNull() }.getOrNull()
        val maxKhz = runCatching { File(maxPath).readText().trim().toIntOrNull() }.getOrNull()
        if (curKhz != null) curFreqs.add(curKhz / 1000) else curFreqs.add(0)
        if (maxKhz != null) maxFreqs.add(maxKhz / 1000) else maxFreqs.add(0)
      }

      val govFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
      val readGov = runCatching { govFile.readText().trim() }.getOrNull()
      if (!readGov.isNullOrEmpty()) {
        governor = readGov
      } else if (privilegeMode != PrivilegeMode.STANDARD) {
        val privGov = privilegeEngine.readPrivilegedSysFile(govFile.absolutePath)
        if (privGov.isNotEmpty()) governor = privGov
      }

      val cpuLoad = readCpuStatPercent()
      val soc = detectSocName()
      val gpu = detectGpuModel()
      val vendor = detectChipsetVendor(soc, gpu)

      CpuSnapshot(
        coreCount = cores,
        coreFrequenciesMhz = curFreqs,
        maxFrequenciesMhz = maxFreqs,
        cpuLoadPercent = cpuLoad,
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
        socModel = soc,
        gpuModel = gpu,
        chipsetVendor = vendor,
        currentGovernor = governor,
      )
    }

  private fun detectChipsetVendor(soc: String, gpu: String): ChipsetVendor {
    val socManufacturer =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else ""
    val combined =
      "$socManufacturer $soc $gpu ${Build.HARDWARE} ${Build.BOARD}".lowercase()
    return when {
      "mediatek" in combined ||
        "dimensity" in combined ||
        "helio" in combined ||
        Regex("\\bmt6[0-9]{3}").containsMatchIn(combined) ||
        "mtk" in combined -> ChipsetVendor.MEDIATEK
      "qualcomm" in combined ||
        "qcom" in combined ||
        "snapdragon" in combined ||
        "adreno" in combined ||
        Regex("\\bsm[0-9]{4}").containsMatchIn(combined) -> ChipsetVendor.SNAPDRAGON
      "tensor" in combined || Regex("\\bgs[0-9]{3}").containsMatchIn(combined) ->
        ChipsetVendor.TENSOR
      "exynos" in combined ||
        "samsung" in combined ||
        "xclipse" in combined ||
        "s5e" in combined -> ChipsetVendor.EXYNOS
      "unisoc" in combined || "sprd" in combined || "spreadtrum" in combined || "ums" in combined ->
        ChipsetVendor.UNISOC
      else -> ChipsetVendor.MEDIATEK
    }
  }

  suspend fun readStorageSnapshot(): StorageSnapshot =
    withContext(Dispatchers.IO) {
      runCatching {
          val stat = StatFs(Environment.getDataDirectory().path)
          val totalBytes = stat.totalBytes.toDouble()
          val availBytes = stat.availableBytes.toDouble()
          val usedBytes = (totalBytes - availBytes).coerceAtLeast(0.0)
          val gbDiv = 1024.0 * 1024.0 * 1024.0
          val totalGb = (totalBytes / gbDiv).toFloat()
          val availGb = (availBytes / gbDiv).toFloat()
          val usedGb = (usedBytes / gbDiv).toFloat()
          val pct =
            if (totalBytes > 0.0) ((usedBytes * 100.0) / totalBytes).toInt().coerceIn(0, 100)
            else 0
          StorageSnapshot(
            totalGb = totalGb,
            availableGb = availGb,
            usedGb = usedGb,
            usedPercent = pct,
          )
        }
        .getOrDefault(StorageSnapshot())
    }

  private fun detectSocName(): String {
    cachedSocName?.let { return it }
    val fromSdk =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
      } else {
        null
      }
    val fromProc =
      runCatching {
          File("/proc/cpuinfo").useLines { lines ->
            lines
              .mapNotNull { line ->
                val lower = line.lowercase()
                if (lower.startsWith("hardware") || lower.startsWith("model name")) {
                  line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                } else {
                  null
                }
              }
              .firstOrNull()
          }
        }
        .getOrNull()
    val resolved = fromSdk ?: fromProc ?: Build.HARDWARE.ifBlank { Build.BOARD }
    cachedSocName = resolved
    return resolved
  }

  private fun detectGpuModel(): String {
    cachedGpuModel?.let { return it }
    val adrenoModel =
      runCatching { File("/sys/class/kgsl/kgsl-3d0/gpu_model").readText().trim() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
    if (adrenoModel != null) {
      cachedGpuModel = adrenoModel
      return adrenoModel
    }

    val eglRenderer = readGlRendererOffscreen()
    val resolved = eglRenderer ?: Build.HARDWARE.ifBlank { "System GPU" }
    cachedGpuModel = resolved
    return resolved
  }

  private fun readGlRendererOffscreen(): String? {
    return runCatching {
        val egl = EGLContext.getEGL() as? EGL10 ?: return@runCatching null
        val display: EGLDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        if (display == EGL10.EGL_NO_DISPLAY) return@runCatching null
        val version = IntArray(2)
        if (!egl.eglInitialize(display, version)) return@runCatching null
        val configSpec =
          intArrayOf(
            EGL10.EGL_RENDERABLE_TYPE,
            4,
            EGL10.EGL_SURFACE_TYPE,
            EGL10.EGL_PBUFFER_BIT,
            EGL10.EGL_NONE,
          )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        egl.eglChooseConfig(display, configSpec, configs, 1, numConfig)
        val config = configs.firstOrNull() ?: return@runCatching null
        val ctx = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, intArrayOf(0x3098, 2, EGL10.EGL_NONE))
        val surface =
          egl.eglCreatePbufferSurface(
            display,
            config,
            intArrayOf(EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE),
          )
        egl.eglMakeCurrent(display, surface, surface, ctx)
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)?.trim()
        egl.eglMakeCurrent(
          display,
          EGL10.EGL_NO_SURFACE,
          EGL10.EGL_NO_SURFACE,
          EGL10.EGL_NO_CONTEXT,
        )
        egl.eglDestroySurface(display, surface)
        egl.eglDestroyContext(display, ctx)
        egl.eglTerminate(display)
        renderer?.takeIf { it.isNotBlank() }
      }
      .getOrNull()
  }

  private fun readCpuStatPercent(): Int {
    return try {
      val firstLine = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return 0
      if (!firstLine.startsWith("cpu ")) return 0
      val parts = firstLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
      if (parts.size < 4) return 0
      val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
      val total = parts.sum()
      val diffTotal = total - prevCpuTotal
      val diffIdle = idle - prevCpuIdle
      prevCpuTotal = total
      prevCpuIdle = idle
      if (diffTotal <= 0L) return 0
      (((diffTotal - diffIdle) * 100L) / diffTotal).toInt().coerceIn(0, 100)
    } catch (_: Exception) {
      0
    }
  }

  fun readDisplaySnapshot(): DisplaySnapshot {
    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    val currentHz = display?.mode?.refreshRate?.roundToInt() ?: 60
    val supportedRates =
      display
        ?.supportedModes
        ?.map { it.refreshRate.roundToInt() }
        ?.distinct()
        ?.sorted()
        ?.ifEmpty { listOf(60) } ?: listOf(60)

    return DisplaySnapshot(
      currentHz = currentHz,
      supportedHz = supportedRates,
      measuredFps = measuredFps,
      frameTimeMs = frameTimeMs,
      jankFrames = jankCount,
    )
  }

  fun trimBackgroundRamUnprivileged(): Int {
    val running = activityManager.runningAppProcesses.orEmpty()
    var trimmed = 0
    running.forEach { proc ->
      if (proc.processName != context.packageName) {
        proc.pkgList?.forEach { pkg ->
          runCatching {
            activityManager.killBackgroundProcesses(pkg)
            trimmed++
          }
        }
      }
    }
    System.gc()
    return trimmed
  }

  fun isPackageInstalled(packageName: String): Boolean {
    if (packageName.isBlank()) return false
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
          packageName,
          PackageManager.PackageInfoFlags.of(0),
        )
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
    return runCatching {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
          } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
          }
        resolved
          .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
            InstalledAppInfo(packageName = pkg, appName = label)
          }
          .distinctBy { it.packageName }
          .sortedBy { it.appName.lowercase() }
      }
      .getOrDefault(emptyList())
  }
}
