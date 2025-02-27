package app.aaps.pump.tandem.common.util

import android.util.Log
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PumpX2L @Inject constructor(val aapsLogger: AAPSLogger) : Timber.DebugTree()  {

    // TODO inline
    inline override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t==null) {
            when (priority) {
                Log.INFO -> aapsLogger.info(LTag.PUMPBTCOMM, "[$tag] - $message")
                Log.WARN -> aapsLogger.warn(LTag.PUMPBTCOMM, "[$tag] - $message")
                Log.ERROR -> aapsLogger.error(LTag.PUMPBTCOMM, "[$tag] - ${message}")
                else -> aapsLogger.debug(LTag.PUMPBTCOMM, "[$tag] - ${message}")
            }
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "$tag - ${message}", t)
        }
    }

}
