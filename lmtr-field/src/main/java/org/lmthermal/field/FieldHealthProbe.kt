package org.lmthermal.field

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File

/** Read-only Android probes for the dedicated foreground field APK, never permission grants.
 * API26 has no authoritative PowerManager thermal status: unknown blocks unattended physical runs.
 * The source/lifecycle owner still enforces USB access; polling does not bypass its authorization.
 */
internal fun fieldHealth(context:Context,foreground:Boolean):FieldHealth {
    val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
    val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
    val power=context.getSystemService(PowerManager::class.java)
    val memory=ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
    val runtime=Runtime.getRuntime()
    val rss=runCatching {File("/proc/self/status").readLines().first {it.startsWith("VmRSS:")}
        .trim().split(Regex("\\s+"))[1].toLong()*1024}.getOrNull()
    return FieldHealth(if(level>=0 && scale>0)level*100/scale else null,
        if(Build.VERSION.SDK_INT>=29)power.currentThermalStatus else null,
        runCatching {StatFs(context.filesDir.path).availableBytes}.getOrNull(),
        runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory()),memory.availMem,memory.lowMemory,rss,
        ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED,
        foreground && power.isInteractive && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
}
