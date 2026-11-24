package com.example.overlay

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.FpsOverlayConfig
import com.example.data.model.OverlayStyle
import com.example.data.prefs.SettingsRepository
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FpsOverlayService : Service() {

  companion object {
    const val ACTION_START = "com.example.overlay.ACTION_START"
    const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
    const val ACTION_UNLOCK_POSITION = "com.example.overlay.ACTION_UNLOCK"

    private const val CHANNEL_ID = "pulsetune_fps_overlay"
    private const val NOTIFICATION_ID = 7001

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start(context: Context) {
      val intent = Intent(context, FpsOverlayService::class.java).setAction(ACTION_START)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, FpsOverlayService::class.java).setAction(ACTION_STOP)
      context.startService(intent)
    }
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private lateinit var windowManager: WindowManager
  private lateinit var displayManager: DisplayManager
  private lateinit var activityManager: ActivityManager
  private lateinit var settingsRepo: SettingsRepository

  private var overlayRoot: LinearLayout? = null
  private var primaryFpsText: TextView? = null
  private var secondaryMetaText: TextView? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  private var currentConfig = FpsOverlayConfig()
  private var currentTargetHz = 0
  private var isDragging = false
  private var lastSyncedPosX = Int.MIN_VALUE
  private var lastSyncedPosY = Int.MIN_VALUE

  private var telemetryJob: Job? = null
  private var configJob: Job? = null

  private var isTrackingFrames = false
  private var frameCount = 0
  private var windowStartNs = 0L
  private var lastFrameNs = 0L
  private var measuredFps = 60
  private var frameTimeMs = 16.6f

  private val frameCallback =
    object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
        if (!isTrackingFrames) return
        if (windowStartNs == 0L) {
          windowStartNs = frameTimeNanos
          lastFrameNs = frameTimeNanos
        }
        val deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000f
        if (deltaMs in 0.5f..250f) {
          frameTimeMs = deltaMs
        }
        lastFrameNs = frameTimeNanos
        frameCount++

        val elapsedNs = frameTimeNanos - windowStartNs
        if (elapsedNs >= 500_000_000L) {
          measuredFps = ((frameCount * 1_000_000_000.0) / elapsedNs).roundToInt().coerceAtLeast(1)
          frameCount = 0
          windowStartNs = frameTimeNanos
          renderStatsNow()
        }
        Choreographer.getInstance().postFrameCallback(this)
      }
    }

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    settingsRepo = SettingsRepository(applicationContext)
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_UNLOCK_POSITION -> {
        serviceScope.launch {
          settingsRepo.saveOverlayConfig(currentConfig.copy(lockPosition = false))
        }
      }
    }

    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return START_NOT_STICKY
    }

    startAsForeground()
    if (overlayRoot == null) {
      attachOverlayView()
      startFrameTracking()
      observeConfigUpdates()
      startPeriodicTelemetryRefresh()
    }
    _isRunning.value = true
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    _isRunning.value = false
    isTrackingFrames = false
    Choreographer.getInstance().removeFrameCallback(frameCallback)
    telemetryJob?.cancel()
    configJob?.cancel()
    overlayRoot?.let { view ->
      runCatching { windowManager.removeView(view) }
    }
    overlayRoot = null
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun startAsForeground() {
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            "Floating FPS Overlay",
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { description = "Persistent real-time FPS and thermal HUD over apps" }
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val openIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val stopIntent =
      PendingIntent.getService(
        this,
        1,
        Intent(this, FpsOverlayService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val unlockIntent =
      PendingIntent.getService(
        this,
        2,
        Intent(this, FpsOverlayService::class.java).setAction(ACTION_UNLOCK_POSITION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("GameTweaks Floating FPS Active")
      .setContentText("Monitoring live FPS & thermals over apps. Drag HUD to move.")
      .setOngoing(true)
      .setContentIntent(openIntent)
      .addAction(0, "Stop", stopIntent)
      .addAction(0, "Unlock Drag", unlockIntent)
      .build()
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun attachOverlayView() {
    val monoFont = runCatching { ResourcesCompat.getFont(this, R.font.jetbrains_mono) }.getOrNull()
    val displayFont = runCatching { ResourcesCompat.getFont(this, R.font.space_grotesk) }.getOrNull()

    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val hPad = dpToPx(12)
        val vPad = dpToPx(8)
        setPadding(hPad, vPad, hPad, vPad)
      }

    val fpsTv =
      TextView(this).apply {
        typeface = displayFont ?: Typeface.DEFAULT_BOLD
        setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
      }

    val metaTv =
      TextView(this).apply {
        typeface = monoFont ?: Typeface.MONOSPACE
        setTextColor(Color.parseColor("#CBD5E1"))
        includeFontPadding = false
      }

    root.addView(fpsTv)
    root.addView(metaTv)

    val overlayType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
      }

    val params =
      WindowManager.LayoutParams(
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          overlayType,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT,
        )
        .apply {
          gravity = Gravity.TOP or Gravity.START
          x = currentConfig.posX
          y = currentConfig.posY
        }

    var startX = 0
    var startY = 0
    var touchStartX = 0f
    var touchStartY = 0f

    root.setOnTouchListener { _, event ->
      if (currentConfig.lockPosition) return@setOnTouchListener false
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          isDragging = true
          startX = params.x
          startY = params.y
          touchStartX = event.rawX
          touchStartY = event.rawY
          true
        }
        MotionEvent.ACTION_MOVE -> {
          params.x = (startX + (event.rawX - touchStartX)).roundToInt()
          params.y = (startY + (event.rawY - touchStartY)).roundToInt()
          runCatching { windowManager.updateViewLayout(root, params) }
          true
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          isDragging = false
          lastSyncedPosX = params.x
          lastSyncedPosY = params.y
          serviceScope.launch { settingsRepo.saveOverlayPosition(params.x, params.y) }
          true
        }
        else -> false
      }
    }

    overlayRoot = root
    primaryFpsText = fpsTv
    secondaryMetaText = metaTv
    layoutParams = params

    applyOverlayAppearance(currentConfig)
    windowManager.addView(root, params)
  }

  private fun observeConfigUpdates() {
    configJob =
      serviceScope.launch {
        settingsRepo.settingsFlow.collect { saved ->
          currentConfig = saved.overlayConfig
          currentTargetHz = saved.targetHz
          val shouldSyncPos =
            !isDragging &&
              (saved.overlayConfig.posX != lastSyncedPosX ||
                saved.overlayConfig.posY != lastSyncedPosY)
          applyOverlayAppearance(currentConfig, restorePos = shouldSyncPos)
        }
      }
  }

  private fun applyOverlayAppearance(config: FpsOverlayConfig, restorePos: Boolean = false) {
    val root = overlayRoot ?: return
    val fpsTv = primaryFpsText ?: return
    val metaTv = secondaryMetaText ?: return
    val params = layoutParams ?: return

    val alphaInt = (config.opacity.coerceIn(0.2f, 0.98f) * 255).roundToInt()
    val bgDrawable =
      GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(14).toFloat()
        setColor(Color.argb(alphaInt, 8, 12, 18))
        setStroke(dpToPx(1), adjustAlpha(config.colorPreset.argbHex, 0.55f))
      }
    root.background = bgDrawable

    fpsTv.setTextColor(config.colorPreset.argbHex)
    fpsTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSize.spValue)
    metaTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, (config.fontSize.spValue - 3.5f).coerceAtLeast(9.5f))
    metaTv.visibility =
      if (config.style == OverlayStyle.COMPACT_PILL) View.GONE else View.VISIBLE

    val baseFlags =
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    params.flags =
      if (config.lockPosition) baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
      else baseFlags

    if (restorePos) {
      params.x = config.posX
      params.y = config.posY
      lastSyncedPosX = config.posX
      lastSyncedPosY = config.posY
    }

    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    if (currentTargetHz <= 0) {
      params.preferredDisplayModeId = 0
      params.preferredRefreshRate = 0f
    } else if (display != null) {
      val activeMode = display.mode
      val sameResModes =
        display.supportedModes.filter {
          it.physicalWidth == activeMode.physicalWidth &&
            it.physicalHeight == activeMode.physicalHeight
        }
      val candidates = sameResModes.ifEmpty { display.supportedModes.toList() }
      val matchingMode =
        candidates.minByOrNull { kotlin.math.abs(it.refreshRate.roundToInt() - currentTargetHz) }
      if (matchingMode != null) {
        params.preferredDisplayModeId = matchingMode.modeId
      }
      params.preferredRefreshRate = currentTargetHz.toFloat()
    }

    runCatching { windowManager.updateViewLayout(root, params) }
    renderStatsNow()
  }

  private fun startFrameTracking() {
    if (isTrackingFrames) return
    isTrackingFrames = true
    windowStartNs = 0L
    lastFrameNs = 0L
    frameCount = 0
    Choreographer.getInstance().postFrameCallback(frameCallback)
  }

  private fun startPeriodicTelemetryRefresh() {
    telemetryJob =
      serviceScope.launch {
        while (isActive) {
          renderStatsNow()
          delay(1000L)
        }
      }
  }

  private fun renderStatsNow() {
    val fpsTv = primaryFpsText ?: return
    val metaTv = secondaryMetaText ?: return

    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    val currentHz = display?.mode?.refreshRate?.roundToInt() ?: 60

    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val tempC = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
    val availMb = memInfo.availMem / (1024L * 1024L)
    val ramPct = (((totalMb - availMb) * 100L) / totalMb).toInt().coerceIn(0, 100)

    fpsTv.text =
      when (currentConfig.style) {
        OverlayStyle.COMPACT_PILL -> "$measuredFps FPS"
        OverlayStyle.GAMING_HUD ->
          if (currentConfig.showRefreshRate) "$measuredFps FPS • ${currentHz}Hz"
          else "$measuredFps FPS"
        OverlayStyle.FULL_TELEMETRY ->
          String.format(Locale.US, "%d FPS (%.1fms)", measuredFps, frameTimeMs)
      }

    if (currentConfig.style != OverlayStyle.COMPACT_PILL) {
      val parts = buildList {
        if (currentConfig.style == OverlayStyle.FULL_TELEMETRY && currentConfig.showRefreshRate) {
          add("${currentHz}Hz")
        }
        if (currentConfig.showTemp && tempC > 0f) {
          add(String.format(Locale.US, "%.1f°C", tempC))
        }
        if (currentConfig.showRam || currentConfig.style == OverlayStyle.FULL_TELEMETRY) {
          add("RAM $ramPct%")
        }
      }
      metaTv.text = parts.joinToString(" • ")
      metaTv.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
    }
  }

  private fun adjustAlpha(color: Int, factor: Float): Int {
    val alpha = (Color.alpha(color) * factor).roundToInt().coerceIn(0, 255)
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
  }

  private fun dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).roundToInt()
}
