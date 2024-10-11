package app.aaps.pump.tandem.common.ble.operations

import app.aaps.pump.tandem.common.ble.BLECommOperationResultType

class BLECommOperationResult {

    var value: ByteArray? = null

    var operationResultType : BLECommOperationResultType = BLECommOperationResultType.RESULT_NONE
        get() = field

    val isSuccessful: Boolean
        get() = operationResultType == BLECommOperationResultType.RESULT_SUCCESS

}