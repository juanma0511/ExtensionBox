package com.extensionbox.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import rikka.shizuku.Shizuku

class SystemAccess(ctx: Context) {

    companion object {
        const val TIER_ROOT = "Root"
        const val TIER_SHIZUKU = "Shizuku"
        const val TIER_NORMAL = "Normal"
    }

    // --- Root Provider Detection ---
    enum class RootProvider(val label: String) {
        NONE("None"),
        MAGISK("Magisk"),
        KERNEL_SU("KernelSU"),
        APATCH("APatch"),
        UNKNOWN("Generic SU")
    }

    private var _rootProvider: RootProvider = RootProvider.NONE
    val rootProvider: RootProvider get() = _rootProvider

    // --- Persistent Shell Session ---
    private class ShellSession {
        private var process: Process? = null
        private var writer: DataOutputStream? = null
        private var reader: BufferedReader? = null
        private val lock = Any()

        fun isAlive(): Boolean {
            return try {
                process?.exitValue() // Throws if alive
                false
            } catch (e: IllegalThreadStateException) {
                true
            }
        }

        fun open(): Boolean {
            if (isAlive()) return true
            return try {
                process = Runtime.getRuntime().exec("su")
                writer = DataOutputStream(process!!.outputStream)
                reader = BufferedReader(InputStreamReader(process!!.inputStream))
                // Test the shell
                exec("echo test") != null
            } catch (e: Exception) {
                false
            }
        }

        fun exec(command: String): String? {
            synchronized(lock) {
                if (!open()) return null
                return try {
                    // Write command + echo marker to know when it ends
                    writer?.writeBytes("$command\necho __END_CMD__\n")
                    writer?.flush()

                    val sb = StringBuilder()
                    var line: String?
                    while (reader?.readLine().also { line = it } != null) {
                        if (line == "__END_CMD__") break
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(line)
                    }
                    sb.toString().trim()
                } catch (e: Exception) {
                    process?.destroy() // Kill dead process
                    null
                }
            }
        }
        
        fun close() {
            try {
                writer?.writeBytes("exit\n")
                writer?.flush()
                process?.waitFor()
            } catch (ignored: Exception) {}
            process = null
        }
    }

    private val shell = ShellSession()
    private val rootAvailable: Boolean

    init {
        // Try to open root shell immediately to trigger Magisk/KSU prompt
        rootAvailable = shell.open()
        if (rootAvailable) {
            detectRootProvider()
        }
    }

    private fun detectRootProvider() {
        val out = shell.exec("magisk -v")
        if (out != null && out.isNotEmpty() && !out.contains("not found")) {
            _rootProvider = RootProvider.MAGISK
            return
        }
        
        // KernelSU usually exposes /proc/version or specific binary checks, 
        // but often acts transparently. Check for ksu specific path if 'magisk' failed.
        val ksuCheck = shell.exec("ls /data/adb/ksu")
        if (ksuCheck != null && !ksuCheck.contains("No such")) {
            _rootProvider = RootProvider.KERNEL_SU
            return
        }
        
        // Simple fallback check for KernelSU via kernel name
        val kernel = shell.exec("uname -r") ?: ""
        if (kernel.contains("ksu", ignoreCase = true)) {
             _rootProvider = RootProvider.KERNEL_SU
             return
        }

        // APatch check (look for apatch binary or specific files)
        val apatchCheck = shell.exec("ls /data/adb/apatch")
        if (apatchCheck != null && !apatchCheck.contains("No such")) {
            _rootProvider = RootProvider.APATCH
            return
        }

        _rootProvider = RootProvider.UNKNOWN
    }

    private val cache = ConcurrentHashMap<String, Pair<String?, Long>>()
    private val CACHE_TTL = 1000L // 1 second

    private val thermalMap = ConcurrentHashMap<String, String>()

    private fun shizukuAvailableNow(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    val tier: String 
        get() = when {
            rootAvailable -> "${TIER_ROOT} (${_rootProvider.label})"
            shizukuAvailableNow() -> TIER_SHIZUKU
            else -> TIER_NORMAL
        }

    fun isEnhanced(): Boolean = rootAvailable || shizukuAvailableNow()

    fun readSysFile(path: String): String? {
        val now = System.currentTimeMillis()
        cache[path]?.let { (value, timestamp) ->
            if (now - timestamp < CACHE_TTL) return value
        }

        val result = readSysFileInternal(path)
        cache[path] = result to now
        return result
    }

    private fun readSysFileInternal(path: String): String? {
        // 1. Try direct read (fastest)
        readFileDirect(path)?.let { return it }
        
        // 2. Try Persistent Root Shell (Interactive)
        if (rootAvailable) {
            shell.exec("cat $path")?.let { return it }
        }
        
        // 3. Try Shizuku (if root failed or not available)
        if (shizukuAvailableNow()) {
            readFileShizuku(path)?.let { return it }
        }
        
        return null
    }

    private fun readFileDirect(path: String): String? {
        return try {
            val f = java.io.File(path)
            if (!f.exists() || !f.canRead()) return null
            f.readText().trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun readFileShizuku(path: String): String? {
        return try {
            val p = Shizuku.newProcess(arrayOf("sh", "-c", "cat $path"), null, null)
            val br = BufferedReader(InputStreamReader(p.inputStream))
            val line = br.readLine()
            br.close()
            p.waitFor()
            if (line != null && line.isNotEmpty()) line.trim() else null
        } catch (e: Exception) {
            null
        }
    }

    // --- Dynamic Thermal Mapping ---

    fun getThermalData(): Map<String, Float> {
        if (thermalMap.isEmpty()) {
            // Initial scan of thermal zones
            val out = if (rootAvailable) shell.exec("ls /sys/class/thermal/thermal_zone*") else null
            val zones = out?.split(Regex("\\s+"))?.filter { it.startsWith("/sys") } 
                ?: (0..20).map { "/sys/class/thermal/thermal_zone$it" }

            for (zone in zones) {
                val type = readSysFile("$zone/type")
                if (type != null) {
                    thermalMap[zone] = type
                }
            }
        }

        val results = mutableMapOf<String, Float>()
        thermalMap.forEach { (path, type) ->
            readSysFile("$path/temp")?.let {
                try {
                    var t = it.toFloat()
                    if (t > 1000) t /= 1000f
                    if (t in -30f..150f) results[type] = t
                } catch (_: Exception) {}
            }
        }
        return results
    }

    // --- CPU Per-Core & GPU Monitoring ---

    fun getCpuCoreFrequencies(): List<Long> {
        val count = getCpuCoreCount()
        val freqs = mutableListOf<Long>()
        for (i in 0 until count) {
            val f = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            freqs.add(f?.toLongOrNull() ?: 0L)
        }
        return freqs
    }

    fun getCpuGovernors(): List<String> {
        val count = getCpuCoreCount()
        val govs = mutableListOf<String>()
        for (i in 0 until count) {
            val g = readSysFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
            govs.add(g ?: "unknown")
        }
        return govs
    }

    fun getGpuData(): Pair<Int, Long> {
        // Qualcomm Adreno
        val adrenoLoad = readSysFile("/sys/class/kgsl/kgsl-3d0/gpu_busy16") // Percentage string or raw
        val adrenoFreq = readSysFile("/sys/class/kgsl/kgsl-3d0/gpuclk")
        
        if (adrenoLoad != null || adrenoFreq != null) {
            val load = try {
                if (adrenoLoad?.contains("%") == true) adrenoLoad.replace("%", "").trim().toInt()
                else adrenoLoad?.trim()?.toInt() ?: 0
            } catch (_: Exception) { 0 }
            val freq = adrenoFreq?.toLongOrNull() ?: 0L
            return Pair(load, freq)
        }

        // Mali
        val maliUtil = readSysFile("/sys/class/misc/mali0/device/utilization")
        if (maliUtil != null) {
            val load = maliUtil.toIntOrNull() ?: 0
            return Pair(load, 0L)
        }

        return Pair(-1, 0L)
    }

    // --- Battery Charge Control (Root Required) ---

    fun setChargingEnabled(enabled: Boolean): Boolean {
        if (!rootAvailable) return false
        val value = if (enabled) "1" else "0"
        val paths = arrayOf(
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/class/power_supply/battery/battery_charging_enabled",
            "/sys/class/power_supply/battery/input_suspend" // Sometimes inverted
        )
        
        for (path in paths) {
            val cmd = "echo $value > $path"
            shell.exec(cmd)
            // Verify? Simple check: if we can read it back
            if (readSysFile(path) == value) return true
        }
        return false
    }

    // --- Advanced Network & Disk I/O ---

    fun getNetworkInterfaceStats(): Map<String, Pair<Long, Long>> {
        val stats = mutableMapOf<String, Pair<Long, Long>>()
        val content = readSysFile("/proc/net/dev") ?: return stats
        content.lines().forEach { line ->
            if (line.contains(":")) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size > 10) {
                    val name = parts[0].replace(":", "")
                    val rx = parts[1].toLongOrNull() ?: 0L
                    val tx = parts[9].toLongOrNull() ?: 0L
                    if (rx > 0 || tx > 0) stats[name] = Pair(rx, tx)
                }
            }
        }
        return stats
    }

    fun getDiskIoStats(): Map<String, Long> {
        val stats = mutableMapOf<String, Long>()
        val content = readSysFile("/proc/diskstats") ?: return stats
        content.lines().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 13) {
                val name = parts[2]
                if (name == "mmcblk0" || name == "sda" || name.startsWith("dm-")) {
                    val readBytes = parts[5].toLongOrNull() ?: 0L // sectors read
                    val writeBytes = parts[9].toLongOrNull() ?: 0L // sectors written
                    stats["${name}_read"] = readBytes * 512
                    stats["${name}_write"] = writeBytes * 512
                }
            }
        }
        return stats
    }

    // --- Existing Hardware Specific Readers ---

    fun readBatteryCurrentMa(ctx: Context): Int {
        if (isEnhanced()) {
            readSysFile("/sys/class/power_supply/battery/current_now")?.let {
                try {
                    return (it.toLong() / 1000).toInt()
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
        } catch (e: Exception) {
            0
        }
    }

    fun readDesignCapacity(ctx: Context): Int {
        if (isEnhanced()) {
            readSysFile("/sys/class/power_supply/battery/charge_full_design")?.let {
                try {
                    val mah = (it.toLong() / 1000).toInt()
                    if (mah in 1..99999) return mah
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val pp = powerProfileClass.getConstructor(Context::class.java).newInstance(ctx)
            val capacity = powerProfileClass.getMethod("getBatteryCapacity").invoke(pp) as Double
            if (capacity > 0) capacity.toInt() else 4000
        } catch (e: Exception) {
            4000
        }
    }

    fun readActualCapacity(): Int {
        if (!isEnhanced()) return -1
        val valStr = readSysFile("/sys/class/power_supply/battery/charge_full") ?: return -1
        return try {
            val mah = (valStr.toLong() / 1000).toInt()
            if (mah in 1..99999) mah else -1
        } catch (e: NumberFormatException) {
            -1
        }
    }

    fun readCycleCount(): Int {
        if (!isEnhanced()) return -1
        val valStr = readSysFile("/sys/class/power_supply/battery/cycle_count") ?: return -1
        return try {
            val cycles = valStr.toInt()
            if (cycles >= 0) cycles else -1
        } catch (e: NumberFormatException) {
            -1
        }
    }

    fun readBatteryTechnology(): String? {
        if (!isEnhanced()) return null
        return readSysFile("/sys/class/power_supply/battery/technology")
    }

    fun readCpuTemp(): Float {
        val paths = arrayOf(
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone2/temp"
        )
        for (path in paths) {
            readSysFile(path)?.let {
                try {
                    var raw = it.toFloat()
                    if (raw > 1000) raw /= 1000f
                    if (raw in 0.1f..149.9f) return raw
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        return Float.NaN
    }

    fun readRealHealthPercentage(ctx: Context): Int {
        val actualCap = readActualCapacity()
        val designCap = readDesignCapacity(ctx)
        if (actualCap <= 0 || designCap <= 0) return -1
        val pct = actualCap * 100 / designCap
        return if (pct in 1..200) pct else -1
    }

    fun readCpuUsageFallback(): Float {
        // Optimized: Use the persistent shell for 'top' if possible to avoid spawn overhead
        val cmd = "top -n 1 -b"
        val output = if (rootAvailable) shell.exec(cmd) else {
            // Fallback to normal exec if no root
            try {
                val p = Runtime.getRuntime().exec(cmd.split(" ").toTypedArray())
                val br = BufferedReader(InputStreamReader(p.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                sb.toString()
            } catch (e: Exception) { "" }
        }
        
        return parseTopOutput(output ?: "")
    }

    private fun parseTopOutput(output: String): Float {
        try {
            output.lines().forEach { line ->
                val l = line.lowercase(java.util.Locale.US)
                if (l.contains("user") && l.contains("sys")) {
                    // Try parsing "400%cpu 14%user 0%nice 20%sys..."
                    // or "User 12%, System 10%..."
                    return parseCpuFromTopLine(l)
                }
            }
        } catch (ignored: Exception) {}
        return -1f
    }

    private fun parseCpuFromTopLine(line: String): Float {
        return try {
            if (line.contains("user") && line.contains("%")) {
                var total = 0f
                val parts = line.split(",")
                for (part in parts) {
                    if (part.contains("user") || part.contains("sys") || part.contains("nice") || part.contains("irq")) {
                        val m = Regex("""(\d+(?:\.\d+)?)\s*%""").find(part)
                        if (m != null) total += m.groupValues[1].toFloat()
                    }
                }
                if (total > 0) return total
            }
            -1f
        } catch (ignored: Exception) {
            -1f
        }
    }

    fun getRunningProcesses(): List<Triple<String, String, String>> {
        val cmd = "top -n 1 -b -m 10"
        val output = if (rootAvailable) shell.exec(cmd) else {
            try {
                val p = Runtime.getRuntime().exec(cmd.split(" ").toTypedArray())
                val br = BufferedReader(InputStreamReader(p.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                sb.toString()
            } catch (e: Exception) { "" }
        }
        
        return parseTopProcesses(output ?: "")
    }

    private fun parseTopProcesses(output: String): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        try {
            var cpuIdx = -1
            var memIdx = -1
            var nameIdx = -1
            
            output.lines().forEach { line ->
                val l = line.trim()
                if (l.contains("PID") && (l.contains("NAME") || l.contains("CMD") || l.contains("COMMAND"))) {
                    val cols = l.split(Regex("\\s+"))
                    cpuIdx = cols.indexOfFirst { it.contains("CPU") }
                    memIdx = cols.indexOfFirst { it.contains("MEM") }
                    nameIdx = cols.indexOfFirst { it.contains("NAME") || it.contains("CMD") || it.contains("COMMAND") }
                    return@forEach
                }
                
                if (cpuIdx != -1 && l.isNotEmpty()) {
                    val parts = l.split(Regex("\\s+"))
                    if (parts.size > nameIdx && parts.size > cpuIdx) {
                        val cpu = parts[cpuIdx].removeSuffix("%") + "%"
                        val mem = if (memIdx != -1 && memIdx < parts.size) parts[memIdx].removeSuffix("%") + "%" else "?"
                        val name = parts[nameIdx].substringAfterLast('/')
                        if (name != "top" && name != "sh" && name != "su") {
                            list.add(Triple(name, cpu, mem))
                        }
                    }
                }
            }
            
            // If header parsing failed, try a best-effort positional approach
            if (list.isEmpty()) {
                output.lines().forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 9 && parts[0].toIntOrNull() != null) {
                        // Assuming CPU is around index 8 or 9
                        val cpu = parts.find { it.contains("%") } ?: parts.getOrNull(8) ?: ""
                        val name = parts.last().substringAfterLast('/')
                        if (name.isNotEmpty() && name != "top") {
                            list.add(Triple(name, cpu, ""))
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return list.filter { it.first.isNotEmpty() }.take(10)
    }

    fun getCpuCoreCount(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            1
        }
    }

    fun onDestroy() {
        shell.close()
        cache.clear()
    }
}
