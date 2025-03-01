package app.aaps.pump.tandem.common.driver

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.defs.BasalProfileStatus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.common.defs.PumpConfigurationTypeInterface
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.tandem.common.driver.connector.response.HomeScreenMirrorDto
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpFeaturesV1Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpFeaturesV2Response
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 13/07/2022
 */
@Singleton
class TandemPumpStatus @Inject constructor(val resourceHelper: ResourceHelper,
                                           val sp: SP,
                                           val rxBus: RxBus
) : PumpStatus(PumpType.TANDEM_T_MOBI_BT) {


    lateinit var pumpDescription: PumpDescription
    var errorDescription: String? = null
    var tandemPumpFirmware: TandemPumpApiVersion = TandemPumpApiVersion.VERSION_2_1_to_2_4
    var serialNumber: Long = 0
    //var ypsoPumpStatusList: YpsoPumpStatusList? = null

    var pumpDriverMode : PumpDriverMode? = null

    // statuses
    //var pumpDeviceState = PumpDriverState.NotInitialized    // TODO rename to pumpConnectionState

    var baseBasalRate = 0.0
    var basalProfileStatus = BasalProfileStatus.NotInitialized
    var basalProfile: Profile? = null

    var bolusStep: Double = 0.1   // ??

    //var maxBolus: Double? = null
    //var maxBasal: Double? = null

    // Tandem specific
    var pumpStatusMirror: HomeScreenMirrorDto? = null
    var settings: MutableMap<PumpConfigurationTypeInterface, Any>? = null
    var featuresV2: PumpFeaturesV2Response? = null
    var featuresV1: PumpFeaturesV1Response? = null
    var lastQualifyingEventsInfo: String? = null

    //var forceRefreshBasalProfile: Boolean = true
    //var basalProfilePump: BasalProfileDto? = null

    override fun initSettings() {
        activeProfileName = "A"
        reservoirRemainingUnits = 75.0
        reservoirFullUnits = 200
        batteryRemaining = 75
        lastConnection = sp.getLong(TandemPumpConst.Statistics.LastGoodPumpCommunicationTime, 0L)
        lastDataTime = lastConnection
    }

    fun resetPumpSettings() {
        // we are storing some pump settings which need to be deleted if pump changes (is unpaired)
        initSettings()
        pumpStatusMirror = null
        tandemPumpFirmware = TandemPumpApiVersion.VERSION_2_1_to_2_4
        basalProfile = null
        basalProfileStatus = BasalProfileStatus.NotInitialized
        //pumpDeviceState = PumpDeviceState.NeverContacted
        serialNumber = 0
        errorDescription = null
        basalsByHour = null
    }

    //var ypsoPumpStatusList: YpsoPumpStatusList? = null

    // fun getPumpStatusValuesForSelectedPump(): YpsoPumpStatusEntry? {
    //     return ypsoPumpStatusList!!.map.get(serialNumber)
    // }
    //
    // fun setPumpStatusValues(entry: YpsoPumpStatusEntry) {
    //     ypsoPumpStatusList!!.map.put(entry.serialNumber, entry)
    //
    // }

    val basalProfileForHour: Double
        get() {
            if (basalsByHour != null) {
                val c = GregorianCalendar()
                val hour = c[Calendar.HOUR_OF_DAY]
                return basalsByHour!![hour]
            }
            return 0.0
        }

    override val errorInfo: String
        get() = if (errorDescription == null) "-" else errorDescription!!







    init {
        initSettings()
    }
}