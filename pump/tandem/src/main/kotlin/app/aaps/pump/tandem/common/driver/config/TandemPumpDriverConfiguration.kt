package app.aaps.pump.tandem.common.driver.config

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.common.driver.PumpDriverConfiguration
import app.aaps.pump.common.driver.ble.PumpBLESelector
import app.aaps.pump.common.driver.db.PumpDriverDatabaseOperation
import app.aaps.pump.common.driver.history.PumpHistoryDataProvider
import javax.inject.Inject

abstract class TandemPumpDriverConfiguration constructor(
    private var pumpBLESelector: TandemBLESelector,
    var pumpHistoryDataProvider: TandemHistoryDataProvider,
    private var pumpType: PumpType
) : PumpDriverConfiguration {


    override fun getPumpType(): PumpType {
        return pumpType
    }

    override fun getPumpBLESelector(): PumpBLESelector {
        return pumpBLESelector
    }

    override fun getPumpHistoryDataProvider(): PumpHistoryDataProvider? {
        return pumpHistoryDataProvider
    }

    override fun getPumpDriverDatabaseOperation(): PumpDriverDatabaseOperation {
        TODO("Not yet implemented")
    }

    override var logPrefix: String = "TandemMobiPlugin::"
    override var canHandleDST: Boolean = false  // TODO must be implemented
    override var hasService: Boolean = true
}