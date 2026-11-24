package com.example.shizuku

import androidx.annotation.Keep
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@Keep
class ShellUserService : IShellService.Stub() {

  override fun exec(command: String): String {
    return try {
      val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()

      var stdout = ""
      var stderr = ""
      val outThread = thread(start = true) {
        stdout = runCatching { process.inputStream.bufferedReader().use { it.readText() }.trim() }.getOrDefault("")
      }
      val errThread = thread(start = true) {
        stderr = runCatching { process.errorStream.bufferedReader().use { it.readText() }.trim() }.getOrDefault("")
      }

      val timeoutSec =
        if (command.contains("cmd package compile") || command.contains("bg-dexopt-job")) 60L
        else 15L

      val completed = process.waitFor(timeoutSec, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        outThread.join(500)
        errThread.join(500)
        return "-1|__DELIM__|$stdout|__DELIM__|Command timed out after ${timeoutSec}s"
      }

      outThread.join(1000)
      errThread.join(1000)
      val exitCode = process.exitValue()
      "$exitCode|__DELIM__|$stdout|__DELIM__|$stderr"
    } catch (e: Exception) {
      "-1|__DELIM__||__DELIM__|${e.message ?: "Execution failed"}"
    }
  }

  override fun readSysFile(path: String): String {
    return try {
      val file = File(path)
      if (file.exists() && file.canRead()) {
        file.readText().trim()
      } else {
        ""
      }
    } catch (_: Exception) {
      ""
    }
  }

  override fun destroy() {
    exitProcess(0)
  }
}
