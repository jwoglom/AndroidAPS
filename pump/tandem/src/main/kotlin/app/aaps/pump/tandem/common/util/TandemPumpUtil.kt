package app.aaps.pump.tandem.common.util

import android.content.Context
import android.util.Log
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.common.defs.PumpDriverState
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import app.aaps.pump.common.utils.PumpUtil
import app.aaps.pump.tandem.common.data.defs.RefreshData
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.events.EventRefreshPumpData
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TandemPumpUtil @Inject constructor(
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    context: Context,
    resourceHelper: ResourceHelper,
    var preferences: Preferences,
    var tandemPumpStatus: TandemPumpStatus

): PumpUtil(aapsLogger, rxBus, context, resourceHelper) {

    fun getTimeFromPumpAsEpochMillis(pumpTime: Long): Long {
        return Dates.fromJan12008EpochSecondsToDate(pumpTime).toEpochMilli();
    }


    override fun resetDriverStatusToConnected() {
        workWithStatusAndCommand(StatusChange.SetStatus, PumpDriverState.Connected, null)
    }


    fun getIntPreferenceOrDefault(intPreferenceKey: IntPreferenceKey, defaultValue: Int? =null): Int {
        return if (preferences.getIfExists(intPreferenceKey)==null)
            defaultValue ?: intPreferenceKey.defaultValue
        else
            preferences.get(intPreferenceKey)
    }


    fun getStringPreferenceOrDefault(stringPreferenceKey: StringPreferenceKey, defaultValue: String? =null): String {
        return if (preferences.getIfExists(stringPreferenceKey)==null)
            defaultValue ?: stringPreferenceKey.defaultValue
        else
            preferences.get(stringPreferenceKey)
    }


    fun getStringPreferenceOrDefaultOrNull(stringPreferenceKey: StringPreferenceKey, defaultValue: String? =null): String? {
        return if (preferences.getIfExists(stringPreferenceKey)==null)
            defaultValue ?: stringPreferenceKey.defaultValue
        else
            preferences.get(stringPreferenceKey)
    }


    fun getBooleanPreferenceOrDefault(booleanPreferenceKey: BooleanPreferenceKey, defaultValue: Boolean? =null): Boolean {
        return if (preferences.getIfExists(booleanPreferenceKey)==null)
            defaultValue ?: booleanPreferenceKey.defaultValue
        else
            preferences.get(booleanPreferenceKey)
    }

    // fun isSame(d1: Double, d2: Double): Boolean {
    //     val diff = d1 - d2
    //     return Math.abs(diff) <= 0.000001
    // }
    //
    // fun isSame(d1: Double, d2: Int): Boolean {
    //     val diff = d1 - d2
    //     return Math.abs(diff) <= 0.000001
    // }



    fun refreshPumpStatus(data: List<RefreshData>) {
        rxBus.send(EventRefreshPumpData(data))
    }

    init {
        driverStatusInternal = PumpDriverState.Connecting
    }



    companion object {

        const val MAX_RETRY = 2


    }
}
