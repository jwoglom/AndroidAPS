package app.aaps.pump.omnipod.common.bledriver.metrics

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager

/**
 * Synchronous, thread-safe probes for environment context captured at
 * `session_start`. Each probe is allowed to return null on failure (missing
 * service, denied permission, unsupported SDK level) — the metric is still
 * useful with a partial picture.
 */
object EnvProbe {

    fun appState(context: Context): String? = try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        when (info.importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND         -> "foreground"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground_service"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE            -> "visible"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE        -> "perceptible"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE            -> "service"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED             -> "cached"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE               -> "gone"
            else                                                                 -> "importance_${info.importance}"
        }
    } catch (_: Throwable) {
        null
    }

    fun powerSaveMode(context: Context): Boolean? = try {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode
    } catch (_: Throwable) {
        null
    }

    fun deviceIdleMode(context: Context): Boolean? = try {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isDeviceIdleMode
    } catch (_: Throwable) {
        null
    }

    fun locationServicesOn(context: Context): Boolean? = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    } catch (_: Throwable) {
        null
    }

    fun bluetoothAdapterState(adapter: BluetoothAdapter?): String? = try {
        when (adapter?.state) {
            BluetoothAdapter.STATE_OFF          -> "OFF"
            BluetoothAdapter.STATE_TURNING_ON   -> "TURNING_ON"
            BluetoothAdapter.STATE_ON           -> "ON"
            BluetoothAdapter.STATE_TURNING_OFF  -> "TURNING_OFF"
            null                                -> null
            else                                -> "UNKNOWN_${adapter?.state}"
        }
    } catch (_: Throwable) {
        null
    }
}
