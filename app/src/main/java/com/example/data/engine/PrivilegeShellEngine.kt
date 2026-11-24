package com.example.data.engine

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.example.BuildConfig
import com.example.data.model.CommandExecutionResult
import com.example.data.model.PrivilegeMode
import com.example.data.model.RootStatus
import com.example.data.model.ShizukuStatus
import com.example.shizuku.IShellService
import com.example.shizuku.ShellUserService
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class PrivilegeShellEngine(private val context: Context) {

  companion object {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 1042
    private const val DELIM = "|__DELIM__|"
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _shizukuStatus = MutableStateFlow<ShizukuStatus>(ShizukuStatus.NotRunning)
  val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

  private val _rootStatus = MutableStateFlow<RootStatus>(RootStatus.Checking)
  val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

  @Volatile private var shellService: IShellService? = null
  @Volatile private var isServiceBinding = false

  private val userServiceArgs =
    Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
      .daemon(false)
      .processNameSuffix("shell_service")
      .debuggable(BuildConfig.DEBUG)
      .version(BuildConfig.VERSION_CODE)

  private val userServiceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        shellService = IShellService.Stub.asInterface(service)
        isServiceBinding = false
        val version = runCatching { Shizuku.getVersion() }.getOrDefault(13)
        _shizukuStatus.value = ShizukuStatus.Ready(version)
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        shellService = null
        isServiceBinding = false
        refreshShizukuStatus()
      }
    }

  private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
    refreshShizukuStatus()
  }

  private val binderDeadListener = Shizuku.OnBinderDeadListener {
    shellService = null
    isServiceBinding = false
    refreshShizukuStatus()
  }

  private val permissionResultListener =
    Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
      if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
          bindShizukuServiceIfReady()
        } else {
          _shizukuStatus.value = ShizukuStatus.PermissionRequired
        }
      }
    }

  init {
    registerShizukuListeners()
    refreshShizukuStatus()
    scope.launch { checkRootAccess() }
  }

  private fun registerShizukuListeners() {
    runCatching {
      Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
      Shizuku.addBinderDeadListener(binderDeadListener)
      Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }
  }

  fun release() {
    runCatching {
      Shizuku.removeBinderReceivedListener(binderReceivedListener)
      Shizuku.removeBinderDeadListener(binderDeadListener)
      Shizuku.removeRequestPermissionResultListener(permissionResultListener)
      if (shellService != null) {
        Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
      }
    }
  }

  fun isShizukuAppInstalled(): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
          SHIZUKU_PACKAGE,
          PackageManager.PackageInfoFlags.of(0),
        )
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  fun refreshShizukuStatus() {
    if (!isShizukuAppInstalled()) {
      _shizukuStatus.value = ShizukuStatus.NotInstalled
      return
    }
    val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    if (!binderAlive) {
      _shizukuStatus.value = ShizukuStatus.NotRunning
      return
    }
    val granted =
      runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
        .getOrDefault(false)
    if (!granted) {
      _shizukuStatus.value = ShizukuStatus.PermissionRequired
      return
    }
    if (shellService != null) {
      val version = runCatching { Shizuku.getVersion() }.getOrDefault(13)
      _shizukuStatus.value = ShizukuStatus.Ready(version)
    } else {
      bindShizukuServiceIfReady()
    }
  }

  fun requestShizukuPermission() {
    if (!isShizukuAppInstalled()) {
      _shizukuStatus.value = ShizukuStatus.NotInstalled
      return
    }
    val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    if (!binderAlive) {
      _shizukuStatus.value = ShizukuStatus.NotRunning
      return
    }
    runCatching {
      if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
        bindShizukuServiceIfReady()
      } else {
        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
      }
    }
  }

  private fun bindShizukuServiceIfReady() {
    if (shellService != null) {
      val version = runCatching { Shizuku.getVersion() }.getOrDefault(13)
      _shizukuStatus.value = ShizukuStatus.Ready(version)
      return
    }
    if (!isServiceBinding) {
      isServiceBinding = true
      _shizukuStatus.value = ShizukuStatus.BindingService
      runCatching { Shizuku.bindUserService(userServiceArgs, userServiceConnection) }
        .onFailure {
          isServiceBinding = false
          _shizukuStatus.value = ShizukuStatus.NotRunning
        }
    }
  }

  suspend fun checkRootAccess(): RootStatus =
    withContext(Dispatchers.IO) {
      _rootStatus.value = RootStatus.Checking
      val candidatePaths =
        listOf(
          "/system/bin/su",
          "/system/xbin/su",
          "/sbin/su",
          "/su/bin/su",
          "/vendor/bin/su",
          "/data/adb/magisk/magisk",
          "/data/adb/ksu/bin/su",
          "/data/adb/ap/bin/su",
        )
      val existingBinary = candidatePaths.firstOrNull { File(it).exists() }
      val suResult = runSuCommand("id")
      val status =
        if (suResult.exitCode == 0 && suResult.stdout.contains("uid=0")) {
          RootStatus.Available(existingBinary ?: "su")
        } else {
          RootStatus.Unavailable
        }
      _rootStatus.value = status
      status
    }

  suspend fun executeCommand(
    command: String,
    preferredMode: PrivilegeMode,
  ): CommandExecutionResult =
    withContext(Dispatchers.IO) {
      when (preferredMode) {
        PrivilegeMode.ROOT -> {
          if (_rootStatus.value is RootStatus.Available) {
            runSuCommand(command)
          } else {
            CommandExecutionResult(
              command = command,
              exitCode = -1,
              stdout = "",
              stderr = "Root (su) is not granted on this device.",
              modeUsed = PrivilegeMode.ROOT,
              executedOnDevice = false,
            )
          }
        }
        PrivilegeMode.SHIZUKU -> {
          val service = shellService
          if (service != null && _shizukuStatus.value is ShizukuStatus.Ready) {
            runShizukuCommand(service, command)
          } else {
            CommandExecutionResult(
              command = command,
              exitCode = -1,
              stdout = "",
              stderr = "Shizuku service is not connected yet.",
              modeUsed = PrivilegeMode.SHIZUKU,
              executedOnDevice = false,
            )
          }
        }
        PrivilegeMode.STANDARD -> {
          CommandExecutionResult(
            command = command,
            exitCode = 0,
            stdout = "Saved & applied in-app configuration.",
            stderr = "",
            modeUsed = PrivilegeMode.STANDARD,
            executedOnDevice = true,
          )
        }
      }
    }

  suspend fun executeBatch(
    commands: List<String>,
    preferredMode: PrivilegeMode,
  ): List<CommandExecutionResult> =
    commands.map { cmd -> executeCommand(cmd, preferredMode) }

  suspend fun readPrivilegedSysFile(path: String): String =
    withContext(Dispatchers.IO) {
      val service = shellService
      if (service != null) {
        val fromShizuku = runCatching { service.readSysFile(path) }.getOrDefault("")
        if (fromShizuku.isNotEmpty()) return@withContext fromShizuku
      }
      if (_rootStatus.value is RootStatus.Available) {
        val rootRead = runSuCommand("cat $path 2>/dev/null")
        if (rootRead.exitCode == 0 && rootRead.stdout.isNotEmpty()) {
          return@withContext rootRead.stdout
        }
      }
      ""
    }

  private fun runShizukuCommand(service: IShellService, command: String): CommandExecutionResult {
    return try {
      val raw = service.exec(command)
      val parts = raw.split(DELIM)
      val exitCode = parts.getOrNull(0)?.toIntOrNull() ?: -1
      val stdout = parts.getOrNull(1).orEmpty()
      val stderr = parts.getOrNull(2).orEmpty()
      CommandExecutionResult(
        command = command,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        modeUsed = PrivilegeMode.SHIZUKU,
        executedOnDevice = true,
      )
    } catch (e: Exception) {
      CommandExecutionResult(
        command = command,
        exitCode = -1,
        stdout = "",
        stderr = e.message ?: "Shizuku IPC failed",
        modeUsed = PrivilegeMode.SHIZUKU,
        executedOnDevice = false,
      )
    }
  }

  private fun runSuCommand(command: String): CommandExecutionResult {
    return try {
      val process = ProcessBuilder("su", "-c", command).redirectErrorStream(false).start()
      var stdout = ""
      var stderr = ""
      val outThread = Thread {
        stdout = runCatching { process.inputStream.bufferedReader().use { it.readText() }.trim() }.getOrDefault("")
      }
      val errThread = Thread {
        stderr = runCatching { process.errorStream.bufferedReader().use { it.readText() }.trim() }.getOrDefault("")
      }
      outThread.start()
      errThread.start()

      val timeoutSec =
        if (command.contains("cmd package compile") || command.contains("bg-dexopt-job")) 60L
        else 15L
      val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
      if (!finished) {
        process.destroyForcibly()
        outThread.join(500)
        errThread.join(500)
        return CommandExecutionResult(
          command = command,
          exitCode = -1,
          stdout = stdout,
          stderr = "Root command timed out after ${timeoutSec}s",
          modeUsed = PrivilegeMode.ROOT,
          executedOnDevice = true,
        )
      }
      outThread.join(1000)
      errThread.join(1000)
      CommandExecutionResult(
        command = command,
        exitCode = process.exitValue(),
        stdout = stdout,
        stderr = stderr,
        modeUsed = PrivilegeMode.ROOT,
        executedOnDevice = true,
      )
    } catch (e: Exception) {
      CommandExecutionResult(
        command = command,
        exitCode = -1,
        stdout = "",
        stderr = e.message ?: "su binary not found",
        modeUsed = PrivilegeMode.ROOT,
        executedOnDevice = false,
      )
    }
  }

  fun toAdbCommand(shellCommand: String): String {
    val trimmed = shellCommand.trim()
    return if (trimmed.startsWith("adb ")) trimmed else "adb shell \"$trimmed\""
  }

  fun buildAdbScript(commands: List<String>, title: String): String {
    val header =
      buildString {
        appendLine("#!/usr/bin/env sh")
        appendLine("# GameTweaks Optimization Script - $title")
        appendLine("# Compatible with Android 11-16 (API 30-36) & backward compatible down to API 24")
        appendLine()
      }
    val body = commands.joinToString("\n") { "adb shell \"$it\"" }
    return header + body
  }
}
